"""HOTCO-CT v4.3 reference dynamics for the DYCONET online service.

Implements manuscript equations 4-16 with the declared 19-node topology,
net-first signed input, bounded Grossberg shunting field, and stage-projected
fixed-step RK4 integration. NumPy is sufficient because online inference is a
small deterministic dynamical system; no learned tensor model is involved.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict

import numpy as np

from input_mapping_v4_3 import MODES, NEEDS, ParticipantInput


MODEL_VERSION = "hotco_ct_v4.3-online.2"
MODEL_NAME = "DYCONET / HOTCO-CT v4.3"


@dataclass(frozen=True)
class HOTCOCTParameters:
    tau: float = 0.8
    decay: float = 0.15
    cognitive_gain: float = 1.0
    need_to_action_gain: float = 1.0
    action_to_need_gain: float = 1.0
    valence_to_action_gain: float = 0.5
    action_to_valence_gain: float = 0.5
    lateral_inhibition: float = 2.0
    dt: float = 0.02
    horizon: float = 40.0
    readout_beta: float = 10.0
    settling_action_threshold: float = 0.01
    settling_state_threshold: float = 0.01
    settling_dwell: float = 1.0
    practical_margin_threshold: float = 0.01


@dataclass
class SimulationResult:
    trajectory: np.ndarray
    topology: np.ndarray
    availability: np.ndarray
    projection_corrections: Dict[str, Dict[str, float | int]]
    context_metadata: Dict[str, Any] = field(default_factory=dict)

    @property
    def final_state(self) -> np.ndarray:
        return self.trajectory[:, -1, :]


class HOTCOCTv43:
    n_needs = len(NEEDS)
    n_modes = len(MODES)
    n_nodes = n_needs + 2 * n_modes

    def __init__(self, *, parameters: HOTCOCTParameters | None = None) -> None:
        self.parameters = parameters or HOTCOCTParameters()
        steps = self.parameters.horizon / self.parameters.dt
        if abs(steps - round(steps)) > 1e-12:
            raise ValueError("horizon must be an integer multiple of dt")
        self.n_steps = int(round(steps))

    def arrays_from_input(
        self, participant: ParticipantInput
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        needs = np.asarray([participant.needs], dtype=np.float64)
        beliefs = np.asarray([participant.beliefs], dtype=np.float64)
        valences = np.asarray([participant.valences], dtype=np.float64)
        availability = np.asarray([participant.availability], dtype=np.float64)
        actions = np.zeros((needs.shape[0], self.n_modes), dtype=np.float64)
        state = np.concatenate((needs, actions, valences), axis=1)
        return state, beliefs, availability

    def build_topology(self, beliefs: np.ndarray) -> np.ndarray:
        beliefs = np.asarray(beliefs, dtype=np.float64)
        if beliefs.ndim != 3 or beliefs.shape[1:] != (self.n_modes, self.n_needs):
            raise ValueError(
                f"beliefs must have shape [batch,{self.n_modes},{self.n_needs}]"
            )
        if not np.isfinite(beliefs).all() or np.any(np.abs(beliefs) > 1.0):
            raise ValueError("beliefs must be finite and lie in [-1,1]")

        p = self.parameters
        batch = beliefs.shape[0]
        topology = np.zeros((batch, self.n_nodes, self.n_nodes), dtype=np.float64)
        n0, a0, v0 = 0, self.n_needs, self.n_needs + self.n_modes
        scaled = (p.cognitive_gain / self.n_needs) * beliefs
        topology[:, a0:v0, n0:a0] = p.need_to_action_gain * scaled
        topology[:, n0:a0, a0:v0] = p.action_to_need_gain * np.transpose(
            scaled, (0, 2, 1)
        )

        identity = np.broadcast_to(np.eye(self.n_modes), (batch, self.n_modes, self.n_modes))
        topology[:, a0:v0, v0:] = p.valence_to_action_gain * identity
        topology[:, v0:, a0:v0] = p.action_to_valence_gain * identity
        return topology

    def bounds_and_mask(
        self, state: np.ndarray, availability: np.ndarray
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        if availability.shape != (state.shape[0], self.n_modes):
            raise ValueError(
                f"availability must have shape [batch,{self.n_modes}]"
            )
        lower = np.zeros_like(state)
        lower[:, self.n_needs + self.n_modes :] = -1.0
        upper = np.ones_like(state)
        upper[:, self.n_needs : self.n_needs + self.n_modes] = availability
        mask = np.ones_like(state)
        mask[:, self.n_needs : self.n_needs + self.n_modes] = availability
        return lower, upper, mask

    def project(self, state: np.ndarray, availability: np.ndarray) -> np.ndarray:
        projected = np.array(state, dtype=np.float64, copy=True)
        projected[:, : self.n_needs] = np.clip(
            projected[:, : self.n_needs], 0.0, 1.0
        )
        action_slice = slice(self.n_needs, self.n_needs + self.n_modes)
        projected[:, action_slice] = (
            np.clip(projected[:, action_slice], 0.0, 1.0) * availability
        )
        projected[:, self.n_needs + self.n_modes :] = np.clip(
            projected[:, self.n_needs + self.n_modes :], -1.0, 1.0
        )
        return projected

    def vector_field(
        self,
        state: np.ndarray,
        topology: np.ndarray,
        availability: np.ndarray,
    ) -> np.ndarray:
        """Equation 12: first aggregate signed input, then rectify its net sign."""

        p = self.parameters
        lower, upper, dynamic_mask = self.bounds_and_mask(state, availability)
        signed_input = np.einsum("bij,bj->bi", topology, state)
        excitation = np.maximum(signed_input, 0.0)
        inhibition = np.maximum(-signed_input, 0.0)

        action_slice = slice(self.n_needs, self.n_needs + self.n_modes)
        actions = state[:, action_slice]
        active_actions = availability * actions
        lateral = p.lateral_inhibition * availability * (
            active_actions.sum(axis=1, keepdims=True) - active_actions
        )
        inhibition = inhibition.copy()
        inhibition[:, action_slice] += lateral

        return (
            dynamic_mask
            * (
                -p.decay * state
                + (upper - state) * excitation
                - (state - lower) * inhibition
            )
            / p.tau
        )

    @staticmethod
    def _empty_correction_stats() -> Dict[str, Dict[str, float | int]]:
        return {
            name: {"count": 0, "max_abs": 0.0}
            for name in ("needs", "actions", "valences")
        }

    def _record_projection(
        self,
        raw: np.ndarray,
        projected: np.ndarray,
        stats: Dict[str, Dict[str, float | int]],
    ) -> None:
        slices = {
            "needs": slice(0, self.n_needs),
            "actions": slice(self.n_needs, self.n_needs + self.n_modes),
            "valences": slice(self.n_needs + self.n_modes, self.n_nodes),
        }
        for name, block in slices.items():
            difference = np.abs(raw[:, block] - projected[:, block])
            stats[name]["count"] = int(stats[name]["count"]) + int(
                np.count_nonzero(difference > 1e-12)
            )
            stats[name]["max_abs"] = max(
                float(stats[name]["max_abs"]),
                float(difference.max(initial=0.0)),
            )

    def _project_and_record(
        self,
        raw: np.ndarray,
        availability: np.ndarray,
        stats: Dict[str, Dict[str, float | int]],
    ) -> np.ndarray:
        projected = self.project(raw, availability)
        self._record_projection(raw, projected, stats)
        return projected

    def rk4_step(
        self,
        state: np.ndarray,
        topology: np.ndarray,
        availability: np.ndarray,
        stats: Dict[str, Dict[str, float | int]],
    ) -> np.ndarray:
        """One RK4 step with projection before every non-initial derivative."""

        dt = self.parameters.dt
        k1 = self.vector_field(state, topology, availability)

        stage2 = self._project_and_record(state + 0.5 * dt * k1, availability, stats)
        k2 = self.vector_field(stage2, topology, availability)

        stage3 = self._project_and_record(state + 0.5 * dt * k2, availability, stats)
        k3 = self.vector_field(stage3, topology, availability)

        stage4 = self._project_and_record(state + dt * k3, availability, stats)
        k4 = self.vector_field(stage4, topology, availability)

        raw_next = state + (dt / 6.0) * (k1 + 2.0 * k2 + 2.0 * k3 + k4)
        return self._project_and_record(raw_next, availability, stats)

    def simulate(self, participant: ParticipantInput) -> SimulationResult:
        initial_state, beliefs, availability = self.arrays_from_input(participant)
        if not np.isfinite(initial_state).all():
            raise ValueError("initial state contains non-finite values")
        if np.any((availability != 0.0) & (availability != 1.0)):
            raise ValueError("availability must be binary")
        if not np.all(availability.sum(axis=1) >= 1):
            raise ValueError("at least one mode must be available")

        projected_initial = self.project(initial_state, availability)
        if not np.array_equal(initial_state, projected_initial):
            raise ValueError("initial state is outside the declared HOTCO-CT bounds")

        topology = self.build_topology(beliefs)
        state = initial_state
        states = [state]
        correction_stats = self._empty_correction_stats()
        for _ in range(self.n_steps):
            state = self.rk4_step(state, topology, availability, correction_stats)
            states.append(state)

        trajectory = np.stack(states, axis=1)
        return SimulationResult(
            trajectory=trajectory,
            topology=topology,
            availability=availability,
            projection_corrections=correction_stats,
            context_metadata={"context_applied": False, "applied": False,
                "context_model": "observations_only", "context_policy": "observations_only",
                "context_model_status": "NOT_APPLIED", "learning_applied": False,
                "state_carryover_applied": False, "warnings": []},
        )
