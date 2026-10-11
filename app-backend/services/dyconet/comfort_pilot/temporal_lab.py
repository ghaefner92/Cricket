"""Offline ordinal state-space candidates. No production API/Passport changes.

Deterministic grid filtering integrates a random-walk intercept and a fixed
rain coefficient. q is latent-location SD per square-root day, not a decay
rate. The rain-only reduction permits matched static/dynamic comparisons.
"""
from dataclasses import dataclass
from functools import lru_cache
import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.special import softmax
from .model import probabilities, initial_location, PRIOR_SCALES

VERSION="d-day-temporal-grid-lab-v1"
DIFFUSIONS=(0.,.15,.4)

@lru_cache(maxsize=32)
def likelihood_grid(grid_size,slope_size,contextual,rain):
    axis=np.linspace(-12,12,grid_size)
    slopes=np.linspace(-3,3,slope_size) if contextual else np.zeros(1)
    likelihood=probabilities(axis[:,None]+slopes[None,:]*np.log1p(rain))
    coded=likelihood@((np.arange(7)-3)/3)
    order=np.argsort(coded,axis=None)
    return likelihood,coded,order

@dataclass
class GridFilter:
    initial:float
    contextual:bool=False
    diffusion:float=0.
    prior_profile:str="moderate"
    provenance:str="declared"
    grid_size:int=241
    slope_size:int=41

    def __post_init__(self):
        if self.diffusion not in DIFFUSIONS or self.prior_profile not in PRIOR_SCALES:raise ValueError("Unknown laboratory candidate")
        if self.provenance not in ("declared","ridge_estimated"):raise ValueError("Unknown provenance")
        if self.grid_size<121 or self.grid_size%2!=1 or self.slope_size<21 or self.slope_size%2!=1:raise ValueError("Use an odd, adequate grid")
        self.axis=np.linspace(-12,12,self.grid_size);self.spacing=self.axis[1]-self.axis[0]
        self.slopes=np.linspace(-3,3,self.slope_size) if self.contextual else np.zeros(1)
        sd=PRIOR_SCALES[self.prior_profile]*(1.5 if self.provenance=="ridge_estimated" else 1.)
        logprior=-.5*((self.axis[:,None]-initial_location(self.initial))/sd)**2-.5*(self.slopes[None,:]/.35)**2
        self.mass=softmax(logprior.reshape(-1)).reshape(logprior.shape)
        self.last_day=0.;self.pending=None;self.count=0;self.max_boundary_mass=0.

    def forecast(self,day,rain):
        if self.pending is not None:raise ValueError("Observe or discard the pending forecast before another")
        if not np.isfinite(day) or day<self.last_day or not np.isfinite(rain) or rain<0:raise ValueError("Invalid calendar/context")
        dt=day-self.last_day
        if self.diffusion and dt:
            # Reflecting numerical boundary; convergence/boundary checks are
            # required because an unbounded Gaussian walk is approximated.
            self.mass=gaussian_filter1d(self.mass,self.diffusion*np.sqrt(dt)/self.spacing,axis=0,mode='reflect',truncate=6.)
            self.mass/=self.mass.sum()
        likelihood,coded,order=likelihood_grid(self.grid_size,self.slope_size,self.contextual,float(rain) if self.contextual else 0.)
        p=(self.mass[:,:,None]*likelihood).sum(axis=(0,1));p/=p.sum()
        flat=coded.reshape(-1)[order];cdf=self.mass.reshape(-1)[order].cumsum()
        interval=np.interp([.05,.95],cdf,flat)
        boundary=float(self.mass[:2].sum()+self.mass[-2:].sum())
        if self.contextual:boundary+=float(self.mass[:,:2].sum()+self.mass[:,-2:].sum())
        self.max_boundary_mass=max(self.max_boundary_mass,boundary)
        self.pending=(day,likelihood,p)
        return {'probabilities':p.tolist(),'normalized_mean':float(p@((np.arange(7)-3)/3)),
            'interval_90':interval.tolist(),'boundary_mass':boundary,'previous_observations':self.count}

    def observe(self,rating):
        if self.pending is None or type(rating) is not int or not 1<=rating<=7:raise ValueError("Ordinal observation must follow its forecast")
        day,likelihood,p=self.pending;evidence=float(p[rating-1])
        self.mass*=likelihood[:,:,rating-1];self.mass/=self.mass.sum()
        self.last_day=day;self.pending=None;self.count+=1
        return evidence

class AveragedFilter:
    """Discrete Bayesian model averaging, updated only AFTER scoring a forecast."""
    def __init__(self,initial,**kwargs):
        self.components=[GridFilter(initial,diffusion=q,**kwargs) for q in DIFFUSIONS]
        self.weights=np.full(len(DIFFUSIONS),1/len(DIFFUSIONS));self.pending=None

    def forecast(self,day,rain):
        forecasts=[f.forecast(day,rain) for f in self.components]
        p=self.weights@np.array([f['probabilities'] for f in forecasts])
        self.pending=forecasts
        # Exact mixture quantiles of coded expectations on all candidate grids.
        reference=self.components[0]
        _,coded,order=likelihood_grid(reference.grid_size,reference.slope_size,reference.contextual,float(rain) if reference.contextual else 0.)
        values=coded.reshape(-1)[order]
        mass=sum(w*f.mass for w,f in zip(self.weights,self.components))
        cdf=mass.reshape(-1)[order].cumsum()
        return {'probabilities':p.tolist(),'normalized_mean':float(p@((np.arange(7)-3)/3)),
            'interval_90':np.interp([.05,.95],cdf,values).tolist(),
            'candidate_weights_before_response':self.weights.tolist(),
            'boundary_mass':max(f['boundary_mass'] for f in forecasts),'previous_observations':forecasts[0]['previous_observations']}

    def observe(self,rating):
        if self.pending is None:raise ValueError("Forecast first")
        evidence=np.array([f.observe(rating) for f in self.components])
        self.weights*=evidence;self.weights/=self.weights.sum();self.pending=None
