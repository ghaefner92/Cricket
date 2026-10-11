"""Offline full-weather filter with independent intercept/rain state processes.

Extends the previously audited Gaussian-prior pCN resample-move algorithm.
No production imports, endpoints or Passport/HOTCO writes.
"""
import numpy as np
from .model import FEATURES, probabilities, ordinal_loglik, design_row
from .temporal_weather import WeatherFilter, weighted_interval, CODING

VERSION = 'd-day-temporal-attribution-v1'
RAIN = FEATURES.index('rain')


class AttributionFilter(WeatherFilter):
    def __init__(self, initial, *, rain_diffusion=0., **kwargs):
        if not np.isfinite(rain_diffusion) or rain_diffusion not in (0., .04, .08, .12):
            raise ValueError('Unknown rain diffusion candidate')
        super().__init__(initial, **kwargs)
        self.rain_q = rain_diffusion
        # Deviations from the uncertain initial coefficient beta[:, RAIN].
        self.rain_path = np.zeros((self.n, 1))

    def forecast(self, day, context):
        result = super().forecast(day, context)
        dt = self.days[-1]-self.days[-2]
        delta = self.rain_path[:, -1].copy()
        if self.rain_q:
            delta += self.rng.normal(0, self.rain_q*np.sqrt(dt), self.n)
        self.rain_path = np.column_stack((self.rain_path, delta))
        x = self.pending[0]
        if self.rain_q:
            p = probabilities(self.path[:, -1]+self.beta@x+delta*x[RAIN])
            mixture = self.weights@p
            self.pending = (x, p)
            result.update(probabilities=mixture.tolist(), normalized_mean=float(mixture@CODING),
                          interval_90=weighted_interval(p@CODING, self.weights))
        a = self.path[:, -1]; b = self.beta[:, RAIN]+delta
        contrast = (probabilities(a+b*np.log(3))-probabilities(a))@CODING
        result.update(intercept_mean=float(self.weights@a), intercept_interval_90=weighted_interval(a, self.weights),
                      rain_coefficient_mean=float(self.weights@b), rain_coefficient_interval_90=weighted_interval(b, self.weights),
                      controlled_rain_contrast_mean=float(self.weights@contrast),
                      controlled_rain_contrast_interval_90=weighted_interval(contrast, self.weights))
        return result

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
            self.rain_path = self.rain_path[indices].copy()
            self.weights.fill(1/self.n); self.resamples += 1
            self._move()
        return evidence

    def _prior_rain_path(self):
        increments = self.rng.normal(size=(self.n, len(self.y)))*self.rain_q*np.sqrt(np.diff(self.days))
        return np.column_stack((np.zeros(self.n), increments.cumsum(axis=1)))

    def _move(self):
        x = np.asarray(self.x); y = np.asarray(self.y)
        rain = x[:, RAIN]
        eta = self.path[:, 1:]+self.beta@x.T+self.rain_path[:, 1:]*rain
        loglik = ordinal_loglik(eta, y).sum(axis=1)
        blocks = ('beta', 'path', 'rain') if self.rain_q else ('beta', 'path')
        for _ in range(self.moves):
            for block in blocks:
                rho = .25 if block == 'beta' else .2
                if block == 'beta':
                    proposed = np.sqrt(1-rho*rho)*self.beta+rho*self.rng.normal(size=self.beta.shape)*self.beta_sd
                    candidate_eta = self.path[:, 1:]+proposed@x.T+self.rain_path[:, 1:]*rain
                elif block == 'path':
                    proposed = self.center+np.sqrt(1-rho*rho)*(self.path-self.center)+rho*(self._prior_path()-self.center)
                    candidate_eta = proposed[:, 1:]+self.beta@x.T+self.rain_path[:, 1:]*rain
                else:
                    proposed = np.sqrt(1-rho*rho)*self.rain_path+rho*self._prior_rain_path()
                    candidate_eta = self.path[:, 1:]+self.beta@x.T+proposed[:, 1:]*rain
                candidate_ll = ordinal_loglik(candidate_eta, y).sum(axis=1)
                accept = np.log(self.rng.random(self.n)) < candidate_ll-loglik
                if block == 'beta': self.beta[accept] = proposed[accept]
                elif block == 'path': self.path[accept] = proposed[accept]
                else: self.rain_path[accept] = proposed[accept]
                loglik[accept] = candidate_ll[accept]
                self.accepted += int(accept.sum()); self.proposed += self.n

    def diagnostics(self):
        return {**super().diagnostics(), 'rain_diffusion':self.rain_q,
                'unique_current_rain_coefficients':int(len(np.unique(self.beta[:, RAIN]+self.rain_path[:, -1])))}
