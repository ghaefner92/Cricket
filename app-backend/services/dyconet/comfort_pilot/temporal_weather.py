"""Offline resample-move ordinal filter with production weather encoding.

Gaussian-prior-invariant pCN proposals receive a full-history likelihood MH
correction. No Gaussian/MAP approximation substitutes for the posterior.
Finite particles and genealogical degeneracy still require numerical audits.
Only the intercept evolves; meteorological coefficients remain static.
"""
import numpy as np
from .model import design_row, FEATURES, RAW_FEATURES, initial_location, PRIOR_SCALES, probabilities, ordinal_loglik

VERSION = 'd-day-full-weather-smc-v1'
CODING = (np.arange(7)-3)/3


def weighted_interval(values, weights):
    order = np.argsort(values)
    return np.interp([.05, .95], np.cumsum(weights[order]), values[order]).tolist()


class WeatherFilter:
    def __init__(self, initial, *, diffusion=0., particles=2048, seed=1,
                 prior_profile='moderate', provenance='declared', moves=4,
                 weather_model_version=None, rain_prior_sd=None):
        from .weather_contract import LEGACY, INTENSITY, FEATURES_V2, RAW_V2, validate_version
        self.weather_model_version = validate_version(weather_model_version or LEGACY)
        features = FEATURES_V2 if self.weather_model_version == INTENSITY else FEATURES
        raw_features = RAW_V2 if self.weather_model_version == INTENSITY else RAW_FEATURES
        if rain_prior_sd is not None and (self.weather_model_version != INTENSITY
                or type(rain_prior_sd) not in (int, float) or rain_prior_sd not in (.175, .35, .7)):
            raise ValueError('Rain prior sensitivity is an explicit V2 laboratory option')
        if diffusion not in (0., .15, .4) or particles < 512 or moves < 1:
            raise ValueError('Unsupported offline numerical configuration')
        if provenance not in ('declared', 'ridge_estimated'):
            raise ValueError('Unknown prior source')
        self.rng = np.random.default_rng(seed)
        self.n = particles; self.q = diffusion; self.moves = moves
        self.center = initial_location(initial)
        self.sd = PRIOR_SCALES[prior_profile]*(1.5 if provenance == 'ridge_estimated' else 1)
        self.beta_sd = np.full(len(features), .35)
        self.beta_sd[len(raw_features)+1:] = .2
        if rain_prior_sd is not None: self.beta_sd[features.index('rainIntensity')] = rain_prior_sd
        self.beta = self.rng.normal(size=(particles, len(features)))*self.beta_sd
        self.path = self.rng.normal(self.center, self.sd, (particles, 1))
        self.weights = np.full(particles, 1/particles)
        self.days = [0.]; self.x = []; self.y = []; self.pending = None
        self.resamples = 0; self.accepted = 0; self.proposed = 0

    def forecast(self, day, context):
        if self.pending is not None or not np.isfinite(day) or day < self.days[-1]:
            raise ValueError('Forecast requires chronological, resolved events')
        x = design_row(context, weather_model_version=self.weather_model_version)[1:]
        dt = day-self.days[-1]
        a = self.path[:, -1]+self.rng.normal(0, self.q*np.sqrt(dt), self.n)
        self.path = np.column_stack((self.path, a)); self.days.append(float(day))
        p = probabilities(a+self.beta@x)
        mixture = self.weights@p
        coded = p@CODING
        self.pending = (x, p)
        return {'probabilities': mixture.tolist(), 'normalized_mean': float(mixture@CODING),
                'interval_90': weighted_interval(coded, self.weights),
                'previous_observations': len(self.y),
                'particle_ess_before_response': float(1/(self.weights@self.weights))}

    def observe(self, rating):
        if self.pending is None or type(rating) is not int or not 1 <= rating <= 7:
            raise ValueError('Observe one ordinal response after its forecast')
        x, p = self.pending
        evidence = float(self.weights@p[:, rating-1])
        self.weights *= p[:, rating-1]; self.weights /= self.weights.sum()
        self.x.append(x); self.y.append(rating); self.pending = None
        if 1/(self.weights@self.weights) < self.n/2:
            positions = (self.rng.random()+np.arange(self.n))/self.n
            indices = np.searchsorted(np.cumsum(self.weights), positions)
            self.beta = self.beta[indices].copy(); self.path = self.path[indices].copy()
            self.weights.fill(1/self.n); self.resamples += 1
            self._move()
        return evidence

    def _prior_path(self):
        a0 = self.rng.normal(self.center, self.sd, (self.n, 1))
        increments = self.rng.normal(size=(self.n, len(self.y)))*self.q*np.sqrt(np.diff(self.days))
        return np.column_stack((a0, a0+increments.cumsum(axis=1)))

    def _move(self):
        x = np.asarray(self.x); y = np.asarray(self.y)
        eta = self.path[:, 1:]+self.beta@x.T
        loglik = ordinal_loglik(eta, y).sum(axis=1)
        for _ in range(self.moves):
            # Each symmetric pCN kernel is reversible with its Gaussian prior.
            # Acceptance therefore requires only the likelihood ratio.
            for block in ('beta', 'path'):
                rho = .25 if block == 'beta' else .2
                if block == 'beta':
                    proposed = np.sqrt(1-rho*rho)*self.beta+rho*self.rng.normal(size=self.beta.shape)*self.beta_sd
                    candidate_eta = self.path[:, 1:]+proposed@x.T
                else:
                    proposed = self.center+np.sqrt(1-rho*rho)*(self.path-self.center)+rho*(self._prior_path()-self.center)
                    candidate_eta = proposed[:, 1:]+self.beta@x.T
                candidate_ll = ordinal_loglik(candidate_eta, y).sum(axis=1)
                accept = np.log(self.rng.random(self.n)) < candidate_ll-loglik
                if block == 'beta': self.beta[accept] = proposed[accept]
                else: self.path[accept] = proposed[accept]
                loglik[accept] = candidate_ll[accept]
                self.accepted += int(accept.sum()); self.proposed += self.n

    def diagnostics(self):
        return {'particles': self.n, 'resampling_events': self.resamples,
                'mh_acceptance': None if not self.proposed else self.accepted/self.proposed,
                'ess': float(1/(self.weights@self.weights)),
                'unique_current_intercepts': int(len(np.unique(self.path[:, -1]))),
                'all_history_retained': True, 'mcmc_convergence_claimed': False}


def average_forecast(filters, forecasts, weights):
    p = weights@np.array([f['probabilities'] for f in forecasts])
    values = []; masses = []
    for f, w in zip(filters, weights):
        x = f.pending[0]
        values.extend(probabilities(f.path[:, -1]+f.beta@x)@CODING)
        masses.extend(w*f.weights)
    return {'probabilities': p.tolist(), 'normalized_mean': float(p@CODING),
            'interval_90': weighted_interval(np.asarray(values), np.asarray(masses)),
            'previous_observations': forecasts[0]['previous_observations'],
            'candidate_weights_before_response': weights.tolist()}
