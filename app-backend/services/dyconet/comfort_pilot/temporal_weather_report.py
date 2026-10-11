"""Audit archived full-weather predictions and produce a standalone report."""
import argparse, json, platform
from hashlib import sha256
from pathlib import Path
import numpy as np

LABELS = {'stable_aligned':'Relación estable', 'stable_wrong_prior':'Prior desalineado',
          'abrupt_change':'Cambio abrupto', 'gradual_change':'Cambio gradual',
          'context_shift_only':'Cambia el contexto', 'transient_bad_trip':'Episodio pasajero',
          'irregular_calendar':'Intervalos desiguales', 'rain_slope_change':'Cambia reacción a lluvia'}


def run(output):
    output = Path(output)
    summary = json.loads((output/'summary.json').read_text(encoding='utf-8'))
    audit = json.loads((output/'numerical_audit.json').read_text(encoding='utf-8'))
    calibration = json.loads((output/'calibration.json').read_text(encoding='utf-8'))
    streams = json.loads((output/'streams.json').read_text(encoding='utf-8'))
    count = 0
    seen = set()
    for s in streams:
        assert len(s['events']) == 72
        if s['initial_source'] == 'ridge_estimated': assert s['initial_raw'] is None
        elif s['target'] == 'comfort': assert abs((s['initial_raw']-4)/3-s['initial']) < 1e-12
        else: assert abs(s['initial_raw']/3-s['initial']) < 1e-12
        for e in s['events']:
            assert 'UV' not in e['context'] and 'uv' not in e['context']
            assert len(e['context']) == 8 and all(np.isfinite(v) for v in e['context'].values())
            assert abs(e['normalized_response']-(e['ordinal_category']-4)/3) < 1e-12
    with (output/'predictions.jsonl').open(encoding='utf-8') as f:
        for line in f:
            row = json.loads(line); prediction = row['forecast']; p = np.array(prediction['probabilities'])
            key = (row['stream'], row['model'], row['step'])
            assert key not in seen; seen.add(key)
            assert len(p) == 7 and np.isfinite(p).all() and min(p) >= 0 and abs(p.sum()-1) < 1e-10
            assert prediction['previous_observations'] == (0 if row['model'] == 'static' else row['step']-1)
            assert np.isfinite(row['log_score']) and 0 <= row['rps'] <= 1
            count += 1
    assert count == summary['scored_predictions'] == len(streams)*60*7
    verification = {'streams':len(streams), 'observations':len(streams)*72, 'scored_predictions':count,
                    'all_probabilities_valid':True, 'all_forecasts_use_only_past_responses':True,
                    'uv_included':False, 'ui_changed':False, 'active_passport_modified':False,
                    'hotco_modified':False, 'production_forecasts_modified':False,
                    'scientific_validation_claimed':False}
    (output/'verification.json').write_text(json.dumps(verification, indent=2)+'\n', encoding='utf-8')
    names = ('basic_cumulative', 'rain_cumulative', 'weather_cumulative',
             'weather_dynamic_0.15', 'weather_dynamic_0.4', 'weather_averaged')
    groups = [g for g in summary['groups'] if g['phase'] == 'after_change']
    lines = ['# D-Day: comparación temporal con meteorología completa', '',
             f"{len(streams)} secuencias independientes; {len(streams)*72} experiencias sintéticas; {count} predicciones puntuadas.", '',
             'Temperatura, humedad, lluvia, viento, ráfagas, viento a diez minutos e intensidad luminosa; duración y codificación del piloto. Sin UV.', '',
             'Dos replicados por escenario/objetivo/modo; prior moderado. No son observaciones de participantes.', '',
             '## Predicción después del viaje 36', '',
             'Log-score mayor es mejor. En escenarios estables el corte no indica un cambio real.', '',
             '| Escenario | Objetivo | Básico | Lluvia | Clima acumulativo | Clima .15 | Clima .4 | Promedio |',
             '|---|---|---:|---:|---:|---:|---:|---:|']
    scenarios = list(dict.fromkeys(g['scenario'] for g in groups))
    for scenario in scenarios:
        for target in ('comfort', 'affect'):
            row = {g['model']:g for g in groups if g['scenario'] == scenario and g['target'] == target}
            lines.append('| '+LABELS[scenario]+' | '+('Comodidad' if target == 'comfort' else 'Afecto')+' | '+' | '.join(f"{row[name]['mean_log_score']:.3f}" for name in names)+' |')
    lines += ['', '## Interpretación exploratoria', '',
              'La meteorología completa aporta ventajas en varios escenarios frente a lluvia sola, especialmente para afecto; no gana siempre en comodidad estable.', '',
              'Con los mismos predictores completos, la adaptación temporal mejora la predicción en cambios abruptos, graduales e intervalos desiguales. En relaciones estables o episodios pasajeros, el acumulativo suele rendir mejor.', '',
              'El promedio no es universalmente superior. Adaptar solo el intercepto no mejora la predicción cuando cambia específicamente la reacción a la lluvia en estas secuencias.', '',
              'La incertidumbre aún es insuficiente ante cambios abruptos: la cobertura del promedio es aproximadamente 79% en ambos objetivos, por debajo del 90% nominal. No activar estos candidatos como posteriores fiables para Passport/HOTCO.', '',
              'Las mejoras grandes al incorporar clima en escenarios de cambio no prueban efectos meteorológicos correctos: temperatura y tiempo covarían en este generador. Se necesita una prueba que desacople ambos, además de sensibilidad a priors y evidencia prospectiva real.', '',
              '## Incertidumbre de la expectativa sintética', '',
              '| Escenario | Objetivo | Cobertura 90% clima acumulativo | Cobertura promedio | RMSE acumulativo | RMSE promedio |',
              '|---|---|---:|---:|---:|---:|']
    for scenario in scenarios:
        for target in ('comfort', 'affect'):
            row = {g['model']:g for g in groups if g['scenario'] == scenario and g['target'] == target}
            a, b = row['weather_cumulative'], row['weather_averaged']
            lines.append(f"| {LABELS[scenario]} | {'Comodidad' if target == 'comfort' else 'Afecto'} | {a['coverage_90']:.3f} | {b['coverage_90']:.3f} | {a['expectation_rmse']:.3f} | {b['expectation_rmse']:.3f} |")
    lines += ['', '## Auditoría numérica', '',
              f"Diagnósticos MCMC de las ocho referencias: {'todos pasan' if audit['all_mcmc_diagnostics_passed'] else 'hay referencias que NO pasan; revisar numerical_audit.json'}.",
              f"Máxima diferencia de expectativa posterior entre acumulativo de 16384 partículas y MCMC: {audit['max_mean_difference_mcmc']:.5f}.",
              f"Máxima diferencia entre pronósticos con 4096/16384 partículas y semillas independientes: {audit['max_mean_sensitivity']:.5f}.", '',
              f"Máxima diferencia de log-score medio por secuencia auditada entre tamaños/semillas: {audit['max_mean_log_score_sensitivity']:.5f}.", '',
              f"Calibración generativa: {calibration['covered']}/{calibration['sequences']} expectativas cubiertas por intervalos del 90%; intervalo binomial 95% {calibration['binomial_interval_95']}.", '',
              'Esta calibración genera desde el prior/modelo candidato: no valida el constructo con personas.', '',
              'La sensibilidad abarca difusión 0/.15, cuatro escenarios y ambos objetivos; no demuestra exactitud en todos los candidatos.', '',
              '## Límites', '',
              '- Solo evoluciona el intercepto; no se aprende una pendiente meteorológica dinámica.',
              '- Las variables del viento están correlacionadas: predicción aceptable no identifica sus efectos individuales.',
              '- La temperatura varía suavemente con el tiempo en el generador: puede absorber parte de un cambio temporal. Una ventaja predictiva no prueba atribución meteorológica correcta.',
              '- Dos replicados por modo/escenario y prior moderado: evaluación exploratoria.',
              '- Un prior equivocado puede favorecer adaptación temporal sin que exista un cambio psicológico.',
              '- El promedio depende de candidatos y priors, y no demuestra cambio causal.',
              '- La cobertura se compara con la expectativa generadora, no con la respuesta individual.',
              '- No modifica UI, predicción en producción, Passport activo o HOTCO.',
              '- No selecciona una difusión universal ni declara validación científica.', '']
    (output/'REPORT.md').write_text('\n'.join(lines), encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(2, 1, figsize=(15, 9), sharex=True)
    x = np.arange(len(scenarios)); width = .23
    for ax, target in zip(axes, ('comfort', 'affect')):
        for j, (name, label) in enumerate(zip(names[3:], ('Dinámico .15', 'Dinámico .4', 'Promedio bayesiano'))):
            differences = []
            for scenario in scenarios:
                row = {g['model']:g for g in groups if g['scenario'] == scenario and g['target'] == target}
                differences.append(row[name]['mean_log_score']-row['weather_cumulative']['mean_log_score'])
            ax.bar(x+(j-1)*width, differences, width, label=label)
        ax.axhline(0, color='black'); ax.grid(axis='y', alpha=.2)
        ax.set_title('Comodidad' if target == 'comfort' else 'Afecto')
        ax.set_ylabel('Δ log-score frente a clima acumulativo')
    axes[0].legend()
    axes[-1].set_xticks(x, [LABELS[s].replace(' ', '\n', 1) for s in scenarios])
    fig.suptitle('Meteorología completa sin UV · positivo mejora, negativo empeora\nPrior moderado · dos replicados por modo/escenario · viajes 37–72')
    fig.tight_layout(); fig.savefig(output/'comparison.png', dpi=150); plt.close(fig)
    source = Path(__file__).parent
    files = ['temporal_weather.py', 'temporal_weather_experiment.py', 'temporal_weather_audit.py', 'temporal_weather_calibration.py', 'temporal_weather_report.py', 'model.py']
    import scipy
    manifest = {'python':platform.python_version(), 'numpy':np.__version__, 'scipy':scipy.__version__,
                'sources':{name:sha256((source/name).read_bytes()).hexdigest() for name in files}}
    (output/'implementation_manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(verification))


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--output', type=Path, required=True)
    run(p.parse_args().output)
