import unittest
import numpy as np
from hotco_ct_v4_3 import HOTCOCTv43,HOTCOCTParameters
from input_mapping_v4_3 import ParticipantInput
from comfort_pilot.network_sensitivity import simulate_batch


class NetworkSensitivityTest(unittest.TestCase):
    def test_batch_matches_production_solver_for_each_case(self):
        rng=np.random.default_rng(14)
        needs=rng.uniform(0,1,11);beliefs=rng.uniform(-1,1,(3,4,11));valences=rng.uniform(-1,1,(3,4))
        parameters=HOTCOCTParameters(horizon=.2,dt=.02)
        batch=simulate_batch(needs,beliefs,valences,[1,1,0,1],parameters)
        for i in range(3):
            participant=ParticipantInput(agent_id="synthetic",raw_needs={},raw_beliefs={},raw_valences={},raw_availability={},availability_provenance={},raw_ranking=None,
                needs=needs.tolist(),beliefs=beliefs[i].tolist(),valences=valences[i].tolist(),availability=[1,1,0,1],ranking=None,environmental_tolerances={})
            reference=HOTCOCTv43(parameters=parameters).simulate(participant)
            np.testing.assert_allclose(batch.trajectory[i],reference.trajectory[0],atol=1e-14)
            np.testing.assert_allclose(batch.topology[i],reference.topology[0],atol=1e-14)
        self.assertEqual(batch.trajectory.shape[-1],19)

    def test_empty_available_modes_and_out_of_bounds_weights_are_rejected(self):
        with self.assertRaises(ValueError):simulate_batch([.5]*11,np.ones((1,4,11))*1.1,np.zeros((1,4)),[1]*4)
        with self.assertRaises(ValueError):simulate_batch([.5]*11,np.zeros((1,4,11)),np.zeros((1,4)),[0]*4)


if __name__ == "__main__":unittest.main()
