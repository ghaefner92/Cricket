"""Validate paired archives and report prediction separately from attribution."""
import argparse, json, platform
from hashlib import sha256
from pathlib import Path
import numpy as np
from .attribution_experiment import SCENARIOS, SCHEDULES, CANDIDATES

LABELS = {'stable':'Estable', 'context_only':'Solo clima', 'intercept_only':'Solo general',
          'rain_only':'Solo reacción a lluvia', 'both':'Ambos cambios', 'transient':'Episodio pasajero'}


def run(output):
    output = Path(output)
    summary = json.loads((output/'summary.json').read_text(encoding='utf-8'))
    audit = json.loads((output/'numerical_audit.json').read_text(encoding='utf-8'))
    streams = json.loads((output/'streams.json').read_text(encoding='utf-8'))
    design = json.loads((output/'design.json').read_text(encoding='utf-8'))
    ids = {s['id']:s for s in streams}; count = 0; seen = set()
    for s in streams:
        if s['initial_source'] == 'ridge_estimated': assert s['initial_raw'] is None
        else: assert abs((s['initial_raw']-(4 if s['target'] == 'comfort' else 0))/3-s['initial']) < 1e-12
        for e in s['events']:
            assert set(e['context']) == set(design['weather'])|{'duration_seconds'}
            assert all(np.isfinite(v) for v in e['context'].values())
            raw_category = e['raw_response']+(4 if s['target'] == 'affect' else 0)
            assert raw_category == e['ordinal_category']
            assert abs(e['normalized_response']-(raw_category-4)/3) < 1e-12
        if s['scenario'] in ('stable', 'context_only', 'rain_only'):
            assert len({e['truth_intercept'] for e in s['events']}) == 1
        if s['scenario'] in ('stable', 'context_only', 'intercept_only', 'transient'):
            assert len({e['truth_rain_coefficient'] for e in s['events']}) == 1
        baseline = next(t for t in streams if t['block'] == s['block'] and t['target'] == s['target'] and t['scenario'] == 'stable')
        if s['scenario'] != 'context_only': assert [e['context'] for e in s['events']] == [e['context'] for e in baseline['events']]
    with (output/'predictions.jsonl').open(encoding='utf-8') as f:
        for line in f:
            r = json.loads(line); key = (r['stream'], r['model'], r['step'])
            assert key not in seen; seen.add(key); forecast = r['forecast']
            p = np.array(forecast['probabilities'])
            assert len(p) == 7 and min(p) >= 0 and np.isfinite(p).all() and abs(p.sum()-1) < 1e-10
            assert forecast['previous_observations'] == r['step']-1
            assert ids[r['stream']]['block'] == r['block']
            assert np.isfinite(list(r['scores'].values())).all()
            for name in ('interval_90', 'intercept_interval_90', 'rain_coefficient_interval_90', 'controlled_rain_contrast_interval_90'):
                lo, hi = forecast[name]; assert np.isfinite([lo, hi]).all() and lo <= hi
            count += 1
    assert count == summary['scored_predictions'] == len(streams)*60*4
    verification = {'sequences':len(streams), 'independent_weather_blocks':summary['weather_blocks'],
                    'synthetic_target_observations':len(streams)*72,
                    'paired_synthetic_journeys':len(streams)*72//2, 'scored_predictions':count,
                    'paired_contexts_checked':True, 'ordinal_codings_checked':True,
                    'all_forecasts_precede_current_response':True, 'uv_included':False,
                    'ui_changed':False, 'production_changed':False, 'passport_hotco_modified':False,
                    'scientific_validation_claimed':False}
    (output/'verification.json').write_text(json.dumps(verification, indent=2)+'\n', encoding='utf-8')
    selected = [r for r in summary['per_stream_scores'] if r['phase'] == 'after_change']
    lines = ['# D-Day: atribución temporal de clima y cambios personales', '',
             f"{len(streams)} secuencias por objetivo emparejadas, {summary['weather_blocks']} bloques meteorológicos independientes; {len(streams)*72//2} viajes sintéticos con {len(streams)*72} respuestas de comodidad/afecto y {count} predicciones puntuadas.", '',
             'Comodidad y afecto separados; meteorología completa sin UV; prior moderado y dos replicados por modo/diseño. No hay tendencia suave de temperatura con el tiempo.', '',
             'Todos los candidatos aprenden de cada respuesta. El acumulativo supone que la relación permanece estable; los candidatos temporales permiten que cambien componentes de esa relación.', '',
             '## Predicción después del viaje 36', '',
             'Log-score mayor es mejor. Cada celda promedia 16 secuencias (dos diseños × cuatro modos × dos replicados); no equivale a 16 personas reales.', '',
             '| Situación | Objetivo | Acumulativo | General dinámico | Lluvia dinámica | Ambos dinámicos |',
             '|---|---|---:|---:|---:|---:|']
    aggregate = []
    for scenario in SCENARIOS:
        for target in ('comfort', 'affect'):
            group = [r for r in selected if r['scenario'] == scenario and r['target'] == target]
            means = {}
            for name in CANDIDATES:
                g = [r for r in group if r['model'] == name]
                item = {'scenario':scenario, 'target':target, 'model':name,
                        **{k:float(np.mean([r[k] for r in g])) for k in g[0] if isinstance(g[0][k], (float, int)) and k not in ('replicate', 'events')}}
                aggregate.append(item); means[name] = item['log_score']
            lines.append('| '+LABELS[scenario]+' | '+('Comodidad' if target == 'comfort' else 'Afecto')+' | '+' | '.join(f'{means[n]:.3f}' for n in CANDIDATES)+' |')
    case = {}
    for name in CANDIDATES:
        g = [r for r in selected if r['scenario'] == 'rain_only' and r['schedule'] == 'correlated_weather' and r['target'] == 'affect' and r['model'] == name]
        case[name] = {'score':float(np.mean([r['log_score'] for r in g])),
                      'general_rmse':float(np.sqrt(np.mean([r['intercept_squared_error'] for r in g]))),
                      'rain_rmse':float(np.sqrt(np.mean([r['rain_coefficient_squared_error'] for r in g])))}
    c, a, b, joint = [case[n] for n in CANDIDATES]
    example = (f"En afecto con clima correlacionado, acumulativo: log-score {c['score']:.3f}, RMSE general {c['general_rmse']:.3f}, RMSE lluvia {c['rain_rmse']:.3f}. "
               f"General dinámico: {a['score']:.3f}, {a['general_rmse']:.3f}, {a['rain_rmse']:.3f}, respectivamente. "
               'La verdad general permanece estable: comparar atribución y predicción por separado.')
    rain_example = (f"Lluvia dinámica: log-score {b['score']:.3f}, RMSE general {b['general_rmse']:.3f}, RMSE lluvia {b['rain_rmse']:.3f}. "
                    f"Ambos dinámicos: {joint['score']:.3f}, {joint['general_rmse']:.3f}, {joint['rain_rmse']:.3f}. "
                    'No interpretar mayor flexibilidad como garantía de mejor atribución.')
    rain_improvements = 0
    for schedule in SCHEDULES:
        for target in ('comfort', 'affect'):
            g = [r for r in selected if r['scenario'] == 'rain_only' and r['schedule'] == schedule and r['target'] == target]
            cumulative_error = np.mean([r['rain_coefficient_squared_error'] for r in g if r['model'] == 'cumulative'])
            rain_error = np.mean([r['rain_coefficient_squared_error'] for r in g if r['model'] == 'rain_dynamic'])
            rain_improvements += int(rain_error < cumulative_error)
    lines += ['', '## Interpretación del cambio específico de lluvia', '',
              f'Cuando cambia solo la reacción a lluvia, el candidato con lluvia dinámica reduce el error de ese coeficiente frente al acumulativo en {rain_improvements}/4 combinaciones de diseño/objetivo. Estas combinaciones comparten bloques meteorológicos.', '',
              example, '', rain_example, '',
              'Estos ejemplos son promedios sintéticos de ocho secuencias por objetivo/diseño, no inferencias causales sobre participantes. Las tasas/priors siguen siendo candidatos y sus efectos dependen del escenario.', '',
              '## Atribución y cobertura: componentes separados', '',
              'RMSE y cobertura se calculan contra parámetros sintéticos conocidos. Intercepto y coeficiente son unidades latentes; no puntuaciones psicológicas observadas.', '',
              '| Situación | Objetivo | Modelo | RMSE general | RMSE lluvia | Cobertura expectativa 90% | Cobertura general 90% | Cobertura lluvia 90% |',
              '|---|---|---|---:|---:|---:|---:|---:|']
    for r in aggregate:
        lines.append('| '+LABELS[r['scenario']]+' | '+r['target']+' | '+r['model']+' | '+
                     f"{np.sqrt(r['intercept_squared_error']):.3f} | {np.sqrt(r['rain_coefficient_squared_error']):.3f} | {r['latent_expectation_covered_90']:.3f} | {r['intercept_covered_90']:.3f} | {r['rain_coefficient_covered_90']:.3f} |")
    lines += ['', '## Cambio de reacción a lluvia por modo', '',
              'Diferencias de log-score frente al acumulativo; positivo mejora. Cuatro secuencias por fila (dos diseños × dos replicados), no viajes independientes.', '',
              '| Modo | Objetivo | General dinámico Δ | Lluvia dinámica Δ | Ambos dinámicos Δ |',
              '|---|---|---:|---:|---:|']
    for mode in ('walk', 'bike', 'pt', 'car'):
        for target in ('comfort', 'affect'):
            group = [r for r in selected if r['scenario'] == 'rain_only' and r['mode'] == mode and r['target'] == target]
            base = np.mean([r['log_score'] for r in group if r['model'] == 'cumulative'])
            changes = [np.mean([r['log_score'] for r in group if r['model'] == name])-base for name in list(CANDIDATES)[1:]]
            lines.append('| '+mode+' | '+target+' | '+' | '.join(f'{v:+.3f}' for v in changes)+' |')
    lines += ['', '## Incertidumbre pendiente ante cambios', '',
              'Una buena cobertura de la expectativa no garantiza cobertura del componente explicativo. La calibración generativa anterior al escenario no resuelve este problema.', '']
    for scenario, target in (('both', 'comfort'), ('both', 'affect'), ('rain_only', 'affect')):
        g = next(r for r in aggregate if r['scenario'] == scenario and r['target'] == target and r['model'] == 'both_dynamic')
        lines.append(f"- {LABELS[scenario]}, {target}, ambos dinámicos: cobertura de expectativa {g['latent_expectation_covered_90']:.1%}; coeficiente de lluvia {g['rain_coefficient_covered_90']:.1%}, frente al 90% nominal.")
    # Retain paired per-block score differences instead of trip-level pseudo-SEs.
    paired = []
    for r in selected:
        baseline = next(b for b in selected if b['stream'] == r['stream'] and b['model'] == 'cumulative')
        paired.append({'stream':r['stream'], 'block':r['block'], 'scenario':r['scenario'], 'schedule':r['schedule'],
                       'target':r['target'], 'mode':r['mode'], 'model':r['model'],
                       'log_score_difference_vs_cumulative':r['log_score']-baseline['log_score']})
    (output/'paired_comparison.json').write_text(json.dumps({'aggregate':aggregate, 'paired_stream_differences':paired}, indent=2)+'\n', encoding='utf-8')
    lines += ['', '## Auditoría numérica', '',
              f"Mayor diferencia de expectativa entre 16384 partículas y rejilla fina: {audit['max_particle_vs_grid_mean_difference']:.5f}.",
              f"Mayor diferencia entre resoluciones de rejilla: {audit['max_grid_resolution_mean_difference']:.5f}; masa máxima en bordes: {audit['max_grid_boundary_mass']:.3g}.",
              f"Sensibilidad completa 4096/16384 partículas y semillas distintas: máximo {audit['max_full_weather_mean_sensitivity']:.5f} en expectativa, {audit['max_full_weather_log_score_sensitivity']:.5f} en log-score medio.", '',
              '| Calibración desde el prior/modelo | Cubiertas | Cobertura | Intervalo binomial 95% |',
              '|---|---:|---:|---|']
    for name, r in audit['calibration_coverage'].items():
        lines.append(f"| {name} | {r['covered']}/64 | {r['coverage']:.3f} | {r['binomial_interval_95'][0]:.3f}–{r['binomial_interval_95'][1]:.3f} |")
    lines += ['', 'La calibración bajo el propio modelo no garantiza cobertura ante saltos o misspecification, ni valida personas.', '',
              '## Límites y siguiente decisión', '',
              '- Se comparan cuatro candidatos predefinidos; no se escoge una difusión universal.',
              '- El prior principal sigue siendo moderado; sensibilidad a priors/tasas queda pendiente.',
              '- Los intervalos aún presentan subcobertura ante saltos; no activar posteriores como estimaciones fiables del Passport.',
              '- Escenarios y objetivos comparten clima: no tratar las 192 secuencias como perfiles independientes.',
              '- Los efectos de PT/car son menores por diseño; dos replicados por modo no garantizan evidencia suficiente.',
              '- Disponibilidad de sensores no implica identificar efectos correlacionados.',
              '- Buena predicción, atribución correcta y cobertura son criterios diferentes.',
              '- El contraste controlado lluvia 0/2 es un diagnóstico sintético, no evidencia causal observada.',
              '- No modifica UI, producción, Passport ni HOTCO; permanece en fase 4.', '']
    (output/'REPORT.md').write_text('\n'.join(lines), encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(2, 2, figsize=(16, 9), sharex=True)
    x = np.arange(len(SCENARIOS)); width = .23
    for column, schedule in enumerate(SCHEDULES):
        for row, target in enumerate(('comfort', 'affect')):
            ax = axes[row, column]
            for j, (name, label) in enumerate(zip(list(CANDIDATES)[1:], ('General dinámico', 'Lluvia dinámica', 'Ambos dinámicos'))):
                values = [np.mean([r['log_score_difference_vs_cumulative'] for r in paired if r['scenario'] == scenario and r['schedule'] == schedule and r['target'] == target and r['model'] == name]) for scenario in SCENARIOS]
                ax.bar(x+(j-1)*width, values, width, label=label)
            ax.axhline(0, color='black'); ax.grid(axis='y', alpha=.2)
            ax.set_title(('Comodidad' if target == 'comfort' else 'Afecto')+' · '+('lluvia independiente' if column == 0 else 'clima correlacionado'))
            ax.set_ylabel('Δ log-score frente a acumulativo')
            ax.set_xticks(x, [LABELS[s].replace(' ', '\n', 1) for s in SCENARIOS])
    axes[0, 0].legend()
    fig.suptitle('Atribución temporal · clima sin tendencia de temperatura · sin UV\nPositivo mejora; negativo empeora · prior moderado · viajes 37–72')
    fig.tight_layout(); fig.savefig(output/'comparison.png', dpi=150); plt.close(fig)
    fig, axes = plt.subplots(2, 2, figsize=(16, 9), sharex=True)
    width = .18
    for row, target in enumerate(('comfort', 'affect')):
        for column, component in enumerate(('intercept', 'rain_coefficient')):
            ax = axes[row, column]
            for j, (name, label) in enumerate(zip(CANDIDATES, ('Acumulativo', 'General dinámico', 'Lluvia dinámica', 'Ambos dinámicos'))):
                values = [np.sqrt(next(g[component+'_squared_error'] for g in aggregate if g['scenario'] == scenario and g['target'] == target and g['model'] == name)) for scenario in SCENARIOS]
                ax.bar(x+(j-1.5)*width, values, width, label=label)
            ax.grid(axis='y', alpha=.2)
            ax.set_title(('Comodidad' if target == 'comfort' else 'Afecto')+' · '+('estado general' if column == 0 else 'coeficiente de lluvia'))
            ax.set_ylabel('RMSE en unidades latentes · menor es mejor')
            ax.set_xticks(x, [LABELS[s].replace(' ', '\n', 1) for s in SCENARIOS])
    axes[0, 0].legend()
    fig.suptitle('Error de atribución frente a verdad sintética\nPromedio de ambos diseños meteorológicos · viajes 37–72 · no validación psicológica')
    fig.tight_layout(); fig.savefig(output/'attribution.png', dpi=150); plt.close(fig)
    import scipy
    source = Path(__file__).parent
    files = ['temporal_attribution.py', 'attribution_experiment.py', 'attribution_reference.py', 'attribution_audit.py', 'attribution_report.py', 'temporal_weather.py', 'temporal_experiment.py', 'model.py']
    manifest = {'python':platform.python_version(), 'numpy':np.__version__, 'scipy':scipy.__version__,
                'sources':{name:sha256((source/name).read_bytes()).hexdigest() for name in files}}
    (output/'implementation_manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(verification))


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--output', type=Path, required=True)
    run(p.parse_args().output)
