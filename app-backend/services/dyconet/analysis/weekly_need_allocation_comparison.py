"""Reproducible paired HOTCO runs for eight predefined synthetic profiles.

No user data, random seeds, contexts, parameter changes or scenario selection
based on results. Run from the repository root with Python.
"""
from __future__ import annotations

import csv
import json
import math
import sys
from dataclasses import asdict, replace
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

SERVICE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE))
from adaptive_passport_onboarding import start_adaptive_passport, complete_adaptive_passport
from adaptive_hotco_adapter import build_adaptive_hotco_input
from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import NEEDS, MODES
from need_allocation import SCHEMA_VERSION
from passport_v2 import _comparative_readout

OUT = SERVICE.parents[2] / "output" / "weekly-normalization-eight-profiles"
MODE_LABELS = {"car": "Auto", "bike": "Bicicleta", "pt": "Transporte público", "walk": "Caminar"}
NEED_LABELS = dict(zip(NEEDS, ["Emisiones", "Actividad física", "Privacidad", "Autonomía", "Coste", "Tiempo", "Seguridad vial", "Seguridad personal", "Comodidad", "Fiabilidad", "Infecciones"]))
COLORS = {"car": "#cf4b4b", "bike": "#269c75", "pt": "#bb8a14", "walk": "#458ae2"}

# Raw synthetic 1..7 beliefs, ordered by canonical NEEDS. These are hypotheses,
# not measured transport properties or claims about a population.
BASE = {
 "car": [1, 1, 7, 7, 2, 7, 4, 6, 6, 6, 7],
 "bike": [6, 7, 5, 6, 6, 5, 3, 4, 3, 5, 7],
 "pt": [6, 3, 2, 3, 5, 5, 6, 4, 4, 4, 2],
 "walk": [7, 7, 5, 6, 7, 2, 5, 4, 3, 6, 7],
}
PROFILES = [
 {"id": "P1", "name": "Ecología", "points": {"pro_env": 4, "physical": 3, "cost": 2, "safety_accident": 1}, "valences": {"car": -2, "bike": 2, "pt": 1, "walk": 1}, "overrides": {}},
 {"id": "P2", "name": "Actividad física", "points": {"physical": 5, "pro_env": 2, "speed": 2, "cost": 1}, "valences": {"car": -1, "bike": 1, "pt": 0, "walk": 1}, "overrides": {"bike__speed": 6}},
 {"id": "P3", "name": "Ahorro", "points": {"cost": 5, "speed": 2, "reliable": 2, "pro_env": 1}, "valences": {"car": 0, "bike": 0, "pt": 0, "walk": 0}, "overrides": {"pt__cost": 7, "pt__reliable": 6, "walk__reliable": 5}},
 {"id": "P4", "name": "Rapidez y autonomía", "points": {"speed": 6, "autonomy": 3, "reliable": 1}, "valences": {"car": 1, "bike": 0, "pt": 0, "walk": -1}, "overrides": {"car__speed": 6, "bike__speed": 6}},
 {"id": "P5", "name": "Comodidad y privacidad", "points": {"comfort": 4, "privacy": 3, "reliable": 2, "safety_crime": 1}, "valences": {"car": 0, "bike": -1, "pt": 0, "walk": 0}, "overrides": {"pt__comfort": 6, "pt__reliable": 6}},
 {"id": "P6", "name": "Seguridad", "points": {"safety_accident": 4, "safety_crime": 3, "health_infection": 2, "reliable": 1}, "valences": {"car": 0, "bike": -1, "pt": 0, "walk": 0}, "overrides": {"car__safety_accident": 5, "walk__safety_crime": 3}},
 {"id": "P7", "name": "Prioridades repartidas", "points": {n: 1 for n in NEEDS if n != "health_infection"}, "valences": {m: 0 for m in MODES}, "overrides": {}},
 {"id": "P8", "name": "Solo emisiones (control)", "points": {"pro_env": 10}, "valences": {m: 0 for m in MODES}, "overrides": {}},
]


def inputs(profile):
    points = {n: profile["points"].get(n, 0) for n in NEEDS}
    assert sum(points.values()) == 10
    # Kotlin roundToInt for nonnegative numbers; Python round uses another tie rule.
    old_raw = {n: 1 + math.floor(6 * p / 10 + .5) for n, p in points.items()}
    raw_beliefs = {m: dict(zip(NEEDS, BASE[m])) for m in MODES}
    for cell, rating in profile["overrides"].items():
        mode, need = cell.split("__"); raw_beliefs[mode][need] = rating
    allocation = {"schema_version": SCHEMA_VERSION, "budget": 10, "points": points}
    completed, starts = {}, {}
    for version, needs in (("old", {"needs": old_raw}), ("exact", {"needs_allocation": allocation})):
        start = start_adaptive_passport({"schema_version": "adaptive_cognitive_passport_onboarding_1.0", "agent_id": profile["id"],
            "responses": {**needs, "valences": profile["valences"]}})
        answers = {q["cell_id"]: raw_beliefs[q["hotco_mode"]][q["model_need"]] for q in start["questions"]}
        completed[version] = complete_adaptive_passport({"schema_version": start["schema_version"], "start": start, "answers": answers})
        starts[version] = start
    assert starts["old"]["questions"] == starts["exact"]["questions"]
    adapted = {version: build_adaptive_hotco_input(value, availability={m: True for m in MODES}).participant for version, value in completed.items()}
    fixed_beliefs = [[(raw_beliefs[m][n]-4)/3 for n in NEEDS] for m in MODES]
    fixed = {version: replace(participant, beliefs=fixed_beliefs, raw_beliefs=raw_beliefs) for version, participant in adapted.items()}
    details = {"points": points, "old_raw_needs": old_raw, "raw_beliefs": raw_beliefs,
        "raw_valences": profile["valences"], "four_observed_beliefs": completed["old"]["observed_measurements"]["beliefs_raw_1_to_7"],
        "old_needs": adapted["old"].needs, "exact_needs": adapted["exact"].needs}
    return {"controlled": fixed, "adaptive": adapted}, details


def simulate_pair(engine, participants):
    runs = {v: engine.simulate(p) for v, p in participants.items()}
    actions = {v: run.trajectory[0, :, len(NEEDS):len(NEEDS)+len(MODES)] for v, run in runs.items()}
    readouts = {v: _comparative_readout(a[-1], np.ones(len(MODES)), engine.parameters.readout_beta) for v, a in actions.items()}
    winners = {v: MODES[int(np.argmax(a))] for v, a in readouts.items()}
    delta = actions["exact"]-actions["old"]
    index = np.unravel_index(np.abs(delta).argmax(), delta.shape)
    derivative = {v: engine.vector_field(run.trajectory[:, 0, :], run.topology, run.availability)[0, len(NEEDS):len(NEEDS)+len(MODES)] for v, run in runs.items()}
    metrics = {"winner_old": winners["old"], "winner_exact": winners["exact"], "winner_changed": winners["old"] != winners["exact"],
        "sum_initial_needs_old": sum(participants["old"].needs), "sum_initial_needs_exact": sum(participants["exact"].needs),
        "max_initial_need_difference": float(np.max(np.abs(np.array(participants["exact"].needs)-participants["old"].needs))),
        "max_belief_difference": float(np.max(np.abs(np.array(participants["exact"].beliefs)-participants["old"].beliefs))),
        "max_initial_action_derivative_difference": float(np.max(np.abs(derivative["exact"]-derivative["old"]))),
        "max_action_trajectory_difference": float(np.abs(delta).max()), "peak_difference_model_time": float(index[0]*engine.parameters.dt),
        "peak_difference_mode": MODES[index[1]], "action_trajectory_rmse": float(np.sqrt(np.mean(delta**2))),
        "max_terminal_action_difference": float(np.abs(delta[-1]).max()),
        "max_terminal_readout_difference_pp": float(np.abs(readouts["exact"]-readouts["old"]).max()*100),
        "readout_old": dict(zip(MODES, map(float, readouts["old"]))), "readout_exact": dict(zip(MODES, map(float, readouts["exact"]))),
        "terminal_actions_old": dict(zip(MODES, map(float, actions["old"][-1]))), "terminal_actions_exact": dict(zip(MODES, map(float, actions["exact"][-1])))}
    return metrics, runs, actions


def write_report(report):
    lines = ["# Ocho perfiles: conversión anterior frente a reparto exacto", "",
        "Perfiles sintéticos diseñados antes de ejecutar las comparaciones. No representan usuarios reales ni una muestra estadística.", "",
        "## Método", "",
        "32 simulaciones: 8 perfiles × 2 conversiones × 2 escenarios. La versión anterior usa `round(6 × puntos / 10) / 6` con el redondeo de Kotlin; la nueva usa `puntos / 10`.", "",
        "En el escenario controlado se mantienen las 44 creencias sintéticas fijas. En el escenario de Passport regenerado se conservan las mismas cuatro respuestas explícitas y el mismo estimador, pero se recalculan las 40 creencias estimadas con las nuevas necesidades. Dentro de cada pareja se conservan valencias, disponibilidad de los cuatro modos y todos los parámetros HOTCO.", "",
        "Sin clima, contexto, rutas reales ni cambios de parámetros. RK4, paso 0,02 y horizonte 40 unidades de tiempo del modelo, que no son minutos de viaje. Se compara el estado terminal del horizonte; no se presupone convergencia asintótica.", "",
        "## Repartos y valencias", "",
        "| Perfil | Reparto de 10 puntos | Valencias: auto, bici, transporte público, caminar |", "|---|---|---|"]
    for p in report["profiles"]:
        distribution = "; ".join(f'{NEED_LABELS[n]} {v}' for n,v in p["input"]["points"].items() if v)
        valences = ", ".join(str(p["input"]["raw_valences"][m]) for m in MODES)
        lines.append(f'| {p["id"]} {p["name"]} | {distribution} | {valences} |')
    lines += ["", "Las valencias son respuestas sintéticas de −3 a +3. Las 44 creencias de cada perfil están en `results.json`, en su escala original de 1 a 7. Son supuestos de esta simulación, no propiedades medidas de los transportes.", "",
        "## Resultados", "", "Δ máximo es el mayor cambio absoluto entre los cuatro modos en la lectura comparativa terminal, expresado en puntos porcentuales. No es una probabilidad calibrada de elegir un transporte.", "",
        "| Perfil | Creencias fijas: anterior → exacta | Δ máximo (pp) | Passport regenerado: anterior → exacta | Δ máximo (pp) |",
        "|---|---|---:|---|---:|"]
    for p in report["profiles"]:
        c,a=p["cases"]["controlled"],p["cases"]["adaptive"]
        lines.append(f'| {p["id"]} {p["name"]} | {MODE_LABELS[c["winner_old"]]} → {MODE_LABELS[c["winner_exact"]]} | {c["max_terminal_readout_difference_pp"]:.4f} | {MODE_LABELS[a["winner_old"]]} → {MODE_LABELS[a["winner_exact"]]} | {a["max_terminal_readout_difference_pp"]:.4f} |')
    changed=sum(p["cases"][s]["winner_changed"] for p in report["profiles"] for s in ("controlled","adaptive"))
    maximum=max(p["cases"]["adaptive"]["max_terminal_readout_difference_pp"] for p in report["profiles"])
    lines += ["", f"En estas 16 comparaciones pareadas hubo {changed} cambios de modo líder. El cambio máximo en la lectura terminal del escenario de Passport regenerado fue {maximum:.4f} pp.", "",
        "El control P8 dio trayectorias idénticas en ambos escenarios: 10 puntos en una sola necesidad producen el mismo peso 1 con ambas conversiones. En P7, diez metas con un punto cada una sumaban 1,6667 de activación inicial; ahora suman 1. P4 pasaba de 1,1667 a 1. En los otros perfiles la suma anterior ya era 1, pero la distribución por necesidad cambiaba.", "",
        "Estos resultados confirman cambios dinámicos aunque se conserve el líder. No demuestran que el líder vaya a conservarse para todos los perfiles, contextos o situaciones próximas a un empate. La diferencia entre los dos escenarios procede de usar 44 creencias declaradas frente a 4 declaradas + 40 estimadas; no debe confundirse con el efecto de la normalización.", "",
        "## Gráficos y datos", "",
        "- `action-trajectories.png` y `.pdf`: acciones a lo largo del tiempo; línea discontinua anterior, continua exacta.",
        "- `terminal-readout-change.png`: cambio por modo y escenario.",
        "- `results.json`: entradas completas, parámetros y métricas por perfil.",
        "- `summary.csv`: 16 comparaciones con diferencias iniciales, de creencias, de derivadas, de trayectoria y terminales.",
        "- `trajectories.npz`: 32 trayectorias completas de los 19 nodos, 2001 instantes cada una.", "",
        "## Reproducir", "", "Desde la raíz del repositorio:", "",
        "`python app-backend/services/dyconet/analysis/weekly_need_allocation_comparison.py`", "",
        "El script verifica la suma exacta de pesos, las creencias idénticas del escenario controlado y la igualdad completa de trayectorias del perfil de control."]
    (OUT/"README.md").write_text("\n".join(lines)+"\n",encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    engine = HOTCOCTv43()
    report = {"model_parameters": asdict(engine.parameters), "canonical_need_order": list(NEEDS), "mode_order": list(MODES),
        "method": "8 predefined synthetic profiles; same explicit responses per pair; 32 runs; all four modes available; no context; standard 40-model-unit horizon; controlled uses all 44 fixed synthetic beliefs, adaptive uses same 4 observations with estimator reconstruction", "profiles": []}
    trajectories, plots, rows = {}, {}, []
    for profile in PROFILES:
        cases, detail = inputs(profile)
        record = {"id": profile["id"], "name": profile["name"], "input": detail, "cases": {}}
        for scenario, participants in cases.items():
            metrics, runs, actions = simulate_pair(engine, participants)
            record["cases"][scenario] = metrics
            plots[profile["id"], scenario] = actions
            for version, run in runs.items(): trajectories[f'{profile["id"]}_{scenario}_{version}'] = run.trajectory[0]
            rows.append({"profile_id": profile["id"], "profile": profile["name"], "scenario": scenario,
                **{k: v for k, v in metrics.items() if not isinstance(v, dict)}})
        report["profiles"].append(record)
    # Numerical controls guard the comparison and the unchanged 10-point endpoint.
    for record in report["profiles"]:
        for scenario, metric in record["cases"].items():
            assert abs(metric["sum_initial_needs_exact"]-1) < 1e-12
            if scenario == "controlled": assert metric["max_belief_difference"] == 0
            if record["id"] == "P8": assert metric["max_action_trajectory_difference"] == 0
    (OUT/"results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    with (OUT/"summary.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer=csv.DictWriter(stream, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
    np.savez_compressed(OUT/"trajectories.npz", **trajectories)
    fig, axes = plt.subplots(8, 2, figsize=(15, 23), sharex=True, sharey=True)
    time=np.arange(engine.n_steps+1)*engine.parameters.dt
    for i, profile in enumerate(PROFILES):
        for j, scenario in enumerate(("controlled", "adaptive")):
            ax=axes[i,j]
            for mi, mode in enumerate(MODES):
                for version, style in (("old", "--"), ("exact", "-")):
                    ax.plot(time, plots[profile["id"], scenario][version][:,mi], style, color=COLORS[mode], linewidth=1.3, alpha=.9)
            ax.set_title(f'{profile["id"]} · {profile["name"]} · {"Creencias fijas" if j==0 else "Passport regenerado"}', fontsize=10)
            ax.grid(alpha=.18); ax.set_ylim(-.015, 1.015)
            if j==0: ax.set_ylabel("Activación de acciones")
    for ax in axes[-1]: ax.set_xlabel("Tiempo del modelo (no minutos reales)")
    from matplotlib.lines import Line2D
    handles=[Line2D([0],[0],color=COLORS[m],label=MODE_LABELS[m]) for m in MODES]+[Line2D([0],[0],color="#333",linestyle="--",label="Anterior"),Line2D([0],[0],color="#333",label="Exacta")]
    fig.legend(handles=handles,loc="upper center",bbox_to_anchor=(.5,.982),ncol=6,frameon=False)
    fig.suptitle("HOTCO: efecto del reparto exacto en ocho perfiles sintéticos",y=.999,fontsize=16)
    fig.tight_layout(rect=(0,0,1,.96)); fig.savefig(OUT/"action-trajectories.png",dpi=150); fig.savefig(OUT/"action-trajectories.pdf"); plt.close(fig)
    fig, axes=plt.subplots(1,2,figsize=(13,6))
    matrices=[]
    for scenario in ("controlled","adaptive"):
        matrices.append(np.array([[100*(r["cases"][scenario]["readout_exact"][m]-r["cases"][scenario]["readout_old"][m]) for m in MODES] for r in report["profiles"]]))
    limit=max(float(np.max(np.abs(a))) for a in matrices) or 1e-12
    for ax, scenario, matrix in zip(axes,("Creencias fijas","Passport regenerado"),matrices):
        chart=ax.imshow(matrix,cmap="RdBu",vmin=-limit,vmax=limit,aspect="auto")
        ax.set_xticks(range(4),[MODE_LABELS[m] for m in MODES],rotation=20,ha="right")
        ax.set_yticks(range(8),[f'{p["id"]} {p["name"]}' for p in PROFILES]); ax.set_title(scenario)
        for i in range(8):
            for j in range(4): ax.text(j,i,f'{matrix[i,j]:+.3g}',ha="center",va="center",fontsize=9)
    fig.colorbar(chart,cax=fig.add_axes([.925,.27,.014,.55]),label="Cambio en lectura comparativa (puntos porcentuales)")
    fig.suptitle("Lectura final: exacta − anterior; no es probabilidad calibrada")
    fig.subplots_adjust(left=.18,right=.87,bottom=.22,wspace=.85,top=.88);fig.savefig(OUT/"terminal-readout-change.png",dpi=150);plt.close(fig)
    write_report(report)
    for record in report["profiles"]:
        print(json.dumps({"id": record["id"], "name":record["name"], **{s:{k:v for k,v in met.items() if k in ("winner_old","winner_exact","sum_initial_needs_old","max_action_trajectory_difference","max_terminal_readout_difference_pp","max_belief_difference")} for s,met in record["cases"].items()}},ensure_ascii=False))
    print("Artifacts:", OUT)


if __name__ == "__main__": main()
