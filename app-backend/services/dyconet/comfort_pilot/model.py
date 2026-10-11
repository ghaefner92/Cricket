"""Ordinal logistic posterior with fixed cutpoints and corrected MCMC sampling.

The Gaussian approximation proposes candidates; Metropolis-Hastings corrects
them against the full likelihood and prior. It is not a Ridge/MAP-only update.
Fixed cutpoints identify location/scale in this small individual pilot. Their
spacing is a modelling assumption, not a validated psychological distance.
"""
from dataclasses import dataclass
from hashlib import sha256
import json
import numpy as np
from scipy.optimize import brentq, minimize
from scipy.special import expit, log_expit, gammaln
from scipy.stats import rankdata, norm

MODEL_VERSION = "d-day-comfort-ordinal-v1"
MODES = ("walk", "bike", "pt", "car")
WEATHER = ("temperature", "humidity", "rain", "windSpeed", "windGust", "windSpeed10Min", "lightIntensity")
RAW_FEATURES = ("duration_seconds", *WEATHER)
FEATURES = (*RAW_FEATURES, "temperature_squared", *("missing:" + k for k in RAW_FEATURES))
CUTPOINTS = np.log(np.arange(1, 7) / np.arange(6, 0, -1))
PRIOR_SCALES = {"weak": 2.4, "moderate": 1.2, "strong": .4}


def probabilities(eta, cutpoint_scale=1.0):
    eta = np.asarray(eta)
    cumulative = expit(CUTPOINTS * cutpoint_scale - eta[..., None])
    return np.diff(np.concatenate((np.zeros((*eta.shape, 1)), cumulative,
                                  np.ones((*eta.shape, 1))), axis=-1), axis=-1)


def signed_expectation(eta, cutpoint_scale=1.0):
    return probabilities(eta, cutpoint_scale) @ (np.arange(1, 8) - 4) / 3


def initial_location(signed):
    if not np.isfinite(signed) or not -1 <= signed <= 1:
        raise ValueError("Initial belief must be in [-1,1]")
    # A finite, uncertain prior cannot encode certainty at a scale endpoint.
    target = np.clip(signed, -.98, .98)
    return float(brentq(lambda x: float(signed_expectation(x)) - target, -15, 15))


def design_row(context, *, weather_model_version=None):
    from .weather_contract import LEGACY, INTENSITY, validate_version, design_row_v2
    version = validate_version(weather_model_version or LEGACY)
    if version == INTENSITY: return design_row_v2(context)
    values, missing = [], []
    for key in RAW_FEATURES:
        raw = context.get(key)
        if raw is None:
            values.append(0.0); missing.append(1.0); continue
        if isinstance(raw, bool) or not isinstance(raw, (int, float)) or not np.isfinite(raw):
            raise ValueError("Context must contain finite raw measurements or null")
        if key == "temperature": v = (raw - 20) / 10
        elif key == "humidity":
            if not 0 <= raw <= 100: raise ValueError("Humidity outside [0,100]")
            v = (raw - 50) / 25
        else:
            if raw < 0: raise ValueError("Negative duration/weather measurement")
            if key == "duration_seconds": v = np.log1p(raw / 60) - np.log(21)
            elif key == "rain": v = np.log1p(raw)
            elif key == "lightIntensity": v = np.log1p(raw) / np.log(10001) - .5
            else: v = raw / 5
        values.append(float(v)); missing.append(0.0)
    return np.array([1., *values, values[1] ** 2, *missing])


def ordinal_loglik(eta, ratings, scale=1.0):
    ratings = np.asarray(ratings, dtype=int)
    cuts = np.r_[-np.inf, CUTPOINTS * scale, np.inf]
    a, b = cuts[ratings] - eta, cuts[ratings - 1] - eta
    return log_expit(a) + log_expit(-b) + np.log(-np.expm1(cuts[ratings - 1] - cuts[ratings]))


def _split(chains):
    half = chains.shape[1] // 2
    return np.concatenate((chains[:, :half], chains[:, -half:]), axis=0)


def _rank(chains):
    ranks = rankdata(chains, axis=None).reshape(chains.shape)
    return norm.ppf((ranks - .375) / (chains.size + .25))


def _rhat(chains):
    n = chains.shape[1]
    within = np.var(chains, axis=1, ddof=1).mean()
    if within <= 0: return float("inf")
    between = n * np.var(chains.mean(axis=1), ddof=1)
    return float(np.sqrt(((n - 1) * within / n + between / n) / within))


def _ess(chains):
    n, m = chains.shape[1], chains.shape[0]
    centered = chains - chains.mean(axis=1, keepdims=True)
    fft = np.fft.rfft(centered, n=2 * n, axis=1)
    acov = np.fft.irfft(fft * np.conjugate(fft), axis=1)[:, :n] / n
    within = acov[:, 0].mean() * n / (n - 1)
    variance = within * (n - 1) / n + np.var(chains.mean(axis=1), ddof=1)
    if variance <= 0: return 0.
    rho = 1 - (within - acov.mean(axis=0)) / variance; rho[0] = 1
    total, previous = 0., float("inf")
    for i in range(0, n - 1, 2):
        pair = float(rho[i] + rho[i + 1])
        if pair < 0: break
        previous = min(previous, pair); total += previous
    return float(min(m * n, m * n / max(1., -1 + 2 * total)))


def diagnostics(chains):
    rhats, esses, tails = [], [], []
    for p in range(chains.shape[-1]):
        split = _split(chains[:, :, p]); ranked = _rank(split)
        rhats.append(max(_rhat(ranked), _rhat(_rank(abs(split - np.median(split))))))
        esses.append(_ess(ranked))
        low,high=np.quantile(split,[.05,.95])
        tails.append(min(_ess((split<=low).astype(float)),_ess((split>=high).astype(float))))
    maximum, minimum = max(rhats), min(esses)
    return {"rank_split_rhat_max": maximum if np.isfinite(maximum) else None,
            "bulk_ess_min": minimum, "tail_ess_min":min(tails), "passed": bool(maximum <= 1.01 and minimum >= 100 and min(tails)>=100),
            "rhat_limit": 1.01, "ess_limit": 100, "parameter_rhat": [v if np.isfinite(v) else None for v in rhats],
            "parameter_bulk_ess": esses,"parameter_tail_ess":tails}


@dataclass
class Posterior:
    chains: np.ndarray
    contextual: bool
    cutpoint_scale: float
    diagnostics: dict
    prior: dict
    event_ids: tuple
    weather_model_version: str = 'd-day-weather-accumulation-v1'

    def predict(self, context):
        row = design_row(context, weather_model_version=self.weather_model_version) if self.contextual else np.ones(1)
        eta = self.chains @ row
        probs = probabilities(eta, self.cutpoint_scale)
        signed = probs @ ((np.arange(1, 8) - 4) / 3)
        q = np.quantile(signed, [.05, .5, .95])
        return {"category_probabilities": probs.mean(axis=(0, 1)).tolist(),
                "signed_belief_mean": float(signed.mean()), "signed_belief_median": float(q[1]),
                "signed_belief_interval_90": [float(q[0]), float(q[2])],
                "interval_kind": "epistemic_interval_for_coded_expected_response_not_individual_rating",
                "positive_probability": float((signed > 0).mean()),
                "negative_probability": float((signed < 0).mean()),
                "expected_ordinal_response": float(4 + 3 * signed.mean())}


def fit(observations, initial_signed, *, contextual=False, prior_profile="moderate",
        provenance="declared", seed=20261009, draws=1024, warmup=256, chains=4,
        cutpoint_scale=1.0, literature_shape=False, weather_model_version=None, rain_prior_sd=None):
    from .weather_contract import LEGACY, INTENSITY, validate_version, specification
    version = validate_version(weather_model_version or LEGACY)
    if rain_prior_sd is not None and (version != INTENSITY or type(rain_prior_sd) not in (int, float)
                                     or rain_prior_sd not in (.175, .35, .7)):
        raise ValueError('Rain prior sensitivity is an explicit V2 laboratory option')
    if version == INTENSITY and literature_shape:
        raise ValueError('Legacy literature-shaped priors cannot be transferred to intensity V2')
    if type(draws) is not int or type(warmup) is not int or type(literature_shape) is not bool or type(cutpoint_scale) not in (int,float):
        raise ValueError("Invalid sampler or prior option type")
    if prior_profile not in PRIOR_SCALES: raise ValueError("Unknown prior profile")
    if provenance not in ("declared", "ridge_estimated"): raise ValueError("Unknown prior provenance")
    if not 128 <= draws <= 4096 or not 0 <= warmup <= 4096 or chains != 4:
        raise ValueError("Pilot uses four chains, 128–4096 draws and 0–4096 warmup")
    if cutpoint_scale not in (.75, 1., 1.5): raise ValueError("Unsupported cutpoint sensitivity")
    ids = tuple(o["event_id"] for o in observations)
    if len(set(ids)) != len(ids): raise ValueError("Duplicate evidence cannot be learned twice")
    if any(type(o["rating"]) is not int or not 1 <= o["rating"] <= 7 for o in observations):
        raise ValueError("Expected ordinal ratings 1–7")
    dimension = 1 + len(FEATURES) if contextual else 1
    x = np.array([design_row(o["context"], weather_model_version=version) if contextual else np.ones(1) for o in observations]).reshape(-1, dimension)
    y = np.array([o["rating"] for o in observations], dtype=int)
    mean = np.zeros(dimension); mean[0] = initial_location(initial_signed)
    sd = np.full(dimension, .35); sd[0] = PRIOR_SCALES[prior_profile] * (1.5 if provenance == "ridge_estimated" else 1)
    if contextual:
        sd[1 + len(RAW_FEATURES) + 1:] = .2
        if rain_prior_sd is not None:
            from .weather_contract import RAW_V2
            sd[1 + RAW_V2.index('rainIntensity')] = rain_prior_sd
        if literature_shape:
            # Qualitative transport/thermal studies motivate a sensitivity
            # candidate, not a measured coefficient for this questionnaire.
            mean[1 + len(RAW_FEATURES)] = -.15
            mean[1 + RAW_FEATURES.index("rain")] = -.15
    inverse = 1 / sd ** 2
    def target(theta):
        theta = np.atleast_2d(theta)
        return -.5 * (((theta - mean) / sd) ** 2).sum(axis=1) + ordinal_loglik(theta @ x.T, y, cutpoint_scale).sum(axis=1)
    def derivatives(theta):
        eta = x @ theta; cuts = np.r_[-np.inf, CUTPOINTS * cutpoint_scale, np.inf]
        upper, lower = expit(cuts[y] - eta), expit(cuts[y - 1] - eta)
        gradient = (theta - mean) * inverse - x.T @ (upper + lower - 1)
        curvature = upper * (1 - upper) + lower * (1 - lower)
        hessian = np.diag(inverse) + (x.T * curvature) @ x
        return gradient, hessian
    rng = np.random.default_rng(seed)
    if len(y):
        result = minimize(lambda t: -target(t)[0], mean, jac=lambda t: derivatives(t)[0],
                          hess=lambda t: derivatives(t)[1], method="trust-exact", options={"gtol":1e-7})
        if not result.success and np.linalg.norm(derivatives(result.x)[0]) > 1e-5:
            raise ValueError("Posterior proposal optimization did not converge")
        center = result.x; covariance = np.linalg.inv(derivatives(center)[1]); chol = np.linalg.cholesky(covariance)
        df = 8.
        def logq(theta):
            whitened = np.linalg.solve(chol, (theta - center).T).T
            return (gammaln((df + dimension) / 2) - gammaln(df / 2) - dimension / 2 * np.log(df * np.pi)
                    - np.log(np.diag(chol)).sum() - (df + dimension) / 2 * np.log1p((whitened ** 2).sum(axis=-1) / df))
        count = draws + warmup
        proposals = center + (rng.normal(size=(chains, count + 1, dimension)) @ chol.T) / np.sqrt(rng.chisquare(df, size=(chains, count + 1, 1)) / df)
        flat = proposals.reshape(-1, dimension)
        ratio = (target(flat) - logq(flat)).reshape(chains, count + 1)
        samples = np.empty((chains, draws, dimension)); acceptance = []
        for chain in range(chains):
            current = 0; accepted = 0; uniforms = np.log(rng.random(count))
            for i in range(1, count + 1):
                if uniforms[i - 1] < ratio[chain, i] - ratio[chain, current]: current = i; accepted += 1
                if i > warmup: samples[chain, i - warmup - 1] = proposals[chain, current]
            acceptance.append(accepted / count)
    else:
        samples = rng.normal(mean, sd, size=(chains, draws, dimension)); acceptance = [1.] * chains
    diag = diagnostics(samples); diag["acceptance_rates"] = acceptance
    prior = {"profile": prior_profile, "provenance": provenance, "mean": mean.tolist(), "sd": sd.tolist(),
             "literature_shape_candidate": literature_shape, "numeric_strength_empirically_validated": False}
    if version == INTENSITY:
        prior['weather_contract'] = specification()
        if rain_prior_sd is not None:
            prior['weather_contract']['rain_coefficient_prior']['sd'] = rain_prior_sd
    return Posterior(samples, contextual, cutpoint_scale, diag, prior, ids, version)


def identity(payload):
    return sha256(json.dumps(payload, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()).hexdigest()
