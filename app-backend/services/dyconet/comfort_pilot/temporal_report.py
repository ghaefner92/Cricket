"""Report the frozen temporal experiment, without selecting an activation rule."""
import argparse,json,platform
from hashlib import sha256
from pathlib import Path
import numpy as np

def run(output):
    output=Path(output);data=json.loads((output/'summary.json').read_text(encoding='utf-8'));groups=data['groups']
    # Clarify provenance in the stream metadata, including exports from the
    # original run. Numerical inputs and predictions remain unchanged.
    streams=json.loads((output/'streams.json').read_text(encoding='utf-8'))
    for stream in streams:
        source='ridge_estimated' if stream['target']=='comfort' and stream['mode'] in ('bike','car') else 'declared'
        stream['initial_source']=source
        if source=='ridge_estimated':stream['initial_raw']=None
    (output/'streams.json').write_text(json.dumps(streams,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    selected=[g for g in groups if g['prior']=='moderate' and g['context_design']=='rain_only' and g['phase']=='after_change']
    lines=['# D-Day: evaluación temporal experimental','',
        f"{data['streams']} secuencias sintéticas independientes; {data['scored_predictions']} predicciones puntuadas.",'',
        'Cada predicción utiliza únicamente respuestas anteriores. Tres repeticiones por escenario/modo/objetivo.',
        'Modelos comparados con idénticos predictores (solo lluvia) y datos. La app conserva su modelo meteorológico completo.',
        'Los candidatos temporales no se activan en Passport/HOTCO.','',
        '## Log-score posterior al punto de cambio (mayor es mejor)','',
        '| Escenario | Objetivo | Acumulativo | Dinámico .15 | Dinámico .4 | Promedio bayesiano |',
        '|---|---|---:|---:|---:|---:|']
    scenarios=list(dict.fromkeys(g['scenario'] for g in selected))
    for scenario in scenarios:
        for target in ('comfort','affect'):
            row={g['model']:g for g in selected if g['scenario']==scenario and g['target']==target}
            lines.append('| '+scenario+' | '+target+' | '+' | '.join(f"{row[m]['mean_log_score']:.3f}" for m in ('cumulative','dynamic_0.15','dynamic_0.4','averaged'))+' |')
    lines+=['','## Incertidumbre y comprobación numérica','',
        '| Escenario | Objetivo | Cobertura 90% acumulativo | Cobertura promedio | RMSE acumulativo | RMSE promedio |',
        '|---|---|---:|---:|---:|---:|']
    for scenario in scenarios:
        for target in ('comfort','affect'):
            row={g['model']:g for g in selected if g['scenario']==scenario and g['target']==target}
            a,b=row['cumulative'],row['averaged']
            lines.append(f"| {scenario} | {target} | {a['latent_expectation_coverage_90']:.2f} | {b['latent_expectation_coverage_90']:.2f} | {a['expectation_rmse']:.3f} | {b['expectation_rmse']:.3f} |")
    diffs=[r['basic_grid_vs_mcmc_absolute_difference'] for r in data['numerical_reference']]
    boundary=max(g['max_grid_boundary_mass'] for g in groups)
    lines+=['',f'Máxima diferencia entre media del acumulativo básico en rejilla y MCMC independiente: {max(diffs):.6f}.',
        f'Máxima masa en bordes de rejilla: {boundary:.6g}.',
        'La comprobación de rejilla más fina está en las pruebas y en sensitivity.json.','',
        '## Límites','',
        '- Difusión .15/.4 son candidatos de sensibilidad en ubicación latente por raíz de día; no ritmos de olvido validados.',
        '- Solo varía el intercepto. Un cambio de la relación con lluvia exige estudiar también pendientes dinámicas.',
        '- El promedio bayesiano tiene una lista finita de candidatos: su peso no demuestra cambio psicológico.',
        '- Cobertura se calcula contra la expectativa sintética conocida, no contra una respuesta aislada.',
        '- Tres repeticiones por modo/escenario son exploratorias; eventos de un individuo no son réplicas independientes.',
        '- Selección equilibrada de modos sintéticos; no se simula elección humana ni efecto de recomendaciones.',
        '- No se define umbral de activación ni se declara validación científica.']
    (output/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    import scipy
    files=[Path(__file__).with_name(name) for name in ('temporal_lab.py','temporal_experiment.py','temporal_calibration.py','temporal_report.py','model.py')]
    manifest={'python':platform.python_version(),'numpy':np.__version__,'scipy':scipy.__version__,
        'files':{p.name:sha256(p.read_bytes()).hexdigest() for p in files},'live_activation':False}
    (output/'implementation_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(2,1,figsize=(11,8),sharex=True)
    for ax,target in zip(axes,('comfort','affect')):
        for i,(model,label) in enumerate((('dynamic_0.15','Dinámico 0.15'),('dynamic_0.4','Dinámico 0.4'),('averaged','Promedio bayesiano'))):
            vals=[next(g['mean_log_score'] for g in selected if g['scenario']==s and g['target']==target and g['model']==model)-next(g['mean_log_score'] for g in selected if g['scenario']==s and g['target']==target and g['model']=='cumulative') for s in scenarios]
            ax.bar(np.arange(len(scenarios))+(i-1)*.24,vals,.24,label=label)
        ax.axhline(0,color='black',linewidth=.8)
        ax.set_ylabel('Ventaja en log-score sobre acumulativo');ax.set_title('Comodidad' if target=='comfort' else 'Afecto');ax.grid(axis='y',alpha=.2)
    axes[0].legend(ncol=3,fontsize=9);axes[-1].set_xticks(np.arange(len(scenarios)),[s.replace('_','\n') for s in scenarios],fontsize=8)
    fig.suptitle('Evaluación sintética temporal · prior moderado · solo lluvia · viajes 37–72\nPositivo: mejora; negativo: empeora · 3 réplicas por modo/escenario')
    fig.tight_layout();fig.savefig(output/'comparison.png',dpi=170);plt.close(fig)
    print(json.dumps({'report':str(output/'REPORT.md'),'max_grid_mcmc_mean_difference':max(diffs),'max_grid_boundary_mass':boundary}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);run(p.parse_args().output)
