"""Feature semantics and candidate use policy; legacy measurements stay legacy."""
import math
import numpy as np

LEGACY = 'd-day-weather-accumulation-v1'
INTENSITY = 'd-day-weather-intensity-v2'
RAW_V2 = ('duration_seconds', 'temperature', 'humidity', 'rainIntensity',
          'windSpeed', 'windGust', 'windSpeed10Min', 'lightIntensity')
FEATURES_V2 = (*RAW_V2, 'temperature_squared', *('missing:' + k for k in RAW_V2))
UNITS_V2 = dict(zip(RAW_V2, ('s', 'C', '%', 'mm/h', 'm/s', 'm/s', 'm/s', 'lux')))
POLICY = {
    'version': 'd-day-weather-use-v2',
    'max_rain_report_age_seconds': 600,
    'max_other_measurement_age_seconds': 1800,
    'unknown_rain_report_time': 'exclude_intensity_keep_other_context',
    'no_usable_weather': 'basic_model_fallback',
    'partial_weather': 'retain_missing_indicators_and_report_limitations',
    'threshold_status': 'operational_candidate_not_empirically_validated',
    'production_learning_enabled': False,
}


def validate_version(version):
    if version not in (LEGACY, INTENSITY):
        raise ValueError('Unsupported weather model version')
    return version


def design_row_v2(context):
    if context.get('rain') is not None or context.get('cumulativeRainfall') is not None:
        raise ValueError('V2 expects rainIntensity in mm/h; accumulated rain is not a feature')
    values, missing = [], []
    for key in RAW_V2:
        raw = context.get(key)
        if raw is None:
            values.append(0.0); missing.append(1.0); continue
        if type(raw) not in (int, float) or not math.isfinite(raw):
            raise ValueError('Context must contain finite raw measurements or null')
        if key == 'temperature':
            if not -40 <= raw <= 80: raise ValueError('Temperature outside supported domain')
            value = (raw - 20) / 10
        elif key == 'humidity':
            if not 0 <= raw <= 100: raise ValueError('Humidity outside [0,100]')
            value = (raw - 50) / 25
        else:
            if raw < 0: raise ValueError('Negative duration/weather measurement')
            if key == 'duration_seconds': value = np.log1p(raw / 60) - np.log(21)
            elif key == 'rainIntensity':
                if raw > 450: raise ValueError('Rain intensity outside supported device domain')
                # Explicit dimensionless reference: 1 mm/h, not 1 mm accumulated.
                value = np.log1p(raw / 1.0)
            elif key == 'lightIntensity': value = np.log1p(raw) / np.log(10001) - .5
            else: value = raw / 5
        values.append(float(value)); missing.append(0.0)
    return np.array([1., *values, values[1] ** 2, *missing])


def specification():
    return {
        'weather_model_version': INTENSITY, 'units': dict(UNITS_V2),
        'feature_columns': ['intercept', *FEATURES_V2],
        'rain_transform': 'log1p(rainIntensity / (1 mm/h))',
        'rain_window_seconds': 600, 'rain_source': 'Sensor:Weather:Winfred.rainIntensity',
        'rain_coefficient_prior': {'mean': 0.0, 'sd': 0.35, 'status': 'candidate_not_empirically_validated'},
        'legacy_rain_coefficients_reused': False, 'use_policy': dict(POLICY),
    }
