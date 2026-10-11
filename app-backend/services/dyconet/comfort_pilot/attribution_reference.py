"""Independent 2D finite-grid benchmark; only for encoded null weather probes."""
import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.special import softmax
from .model import initial_location, probabilities
from .temporal_weather import CODING


class GridReference:
    def __init__(self, initial=1/3, q=.15, rain_q=.08, size=241):
        self.a = np.linspace(-12, 12, size); self.b = np.linspace(-5, 5, size)
        self.q = q; self.rain_q = rain_q; self.day = 0.; self.pending = None
        logprior = -.5*((self.a[:, None]-initial_location(initial))/1.2)**2-.5*(self.b[None, :]/.35)**2
        self.mass = softmax(logprior.reshape(-1)).reshape(logprior.shape)

    def forecast(self, day, rain):
        if self.pending is not None or day < self.day: raise ValueError('Chronological resolved forecast required')
        dt = day-self.day
        for axis, q, spacing in ((0, self.q, self.a[1]-self.a[0]), (1, self.rain_q, self.b[1]-self.b[0])):
            if q and dt: self.mass = gaussian_filter1d(self.mass, q*np.sqrt(dt)/spacing, axis=axis, mode='reflect', truncate=6.)
        self.mass /= self.mass.sum()
        likelihood = probabilities(self.a[:, None]+self.b[None, :]*np.log1p(rain))
        p = (self.mass[:, :, None]*likelihood).sum(axis=(0, 1))
        self.pending = (day, likelihood)
        return {'mean':float(p@CODING), 'a':float(self.mass.sum(axis=1)@self.a),
                'b':float(self.mass.sum(axis=0)@self.b),
                'boundary_mass':float(self.mass[:2].sum()+self.mass[-2:].sum()+self.mass[:, :2].sum()+self.mass[:, -2:].sum())}

    def observe(self, rating):
        day, likelihood = self.pending
        self.mass *= likelihood[:, :, rating-1]; self.mass /= self.mass.sum()
        self.day = day; self.pending = None


def null_context(rain=0.):
    return dict(duration_seconds=1200., temperature=20., humidity=50., rain=float(rain),
                windSpeed=0., windGust=0., windSpeed10Min=0., lightIntensity=float(np.sqrt(10001)-1))
