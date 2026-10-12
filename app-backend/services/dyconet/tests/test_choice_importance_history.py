import copy
import unittest
from choice_evidence_v1 import goals, ChoiceEvidenceError, GOAL_MAP
from choice_story_v1 import render_choice_story

class ChoiceImportanceHistoryTest(unittest.TestCase):
    def fixture(self):
        ratings={k:1+(i%7) for i,k in enumerate(GOAL_MAP)}
        snapshot={'needsSource':'ordinal_importance_1_7','conversion':'rating_minus1_div6_v1','goalRatings':ratings}
        passport={'observed_measurements':{'needs_raw_1_to_7':{n:ratings[k] for k,n in GOAL_MAP.items()}},
                  'profile':{'needs':{n:(ratings[k]-1)/6 for k,n in GOAL_MAP.items()}},
                  'daily_goals':{'local_date':'2026-10-12','timezone':'Europe/Berlin'}}
        return snapshot,passport

    def test_daily_ordinal_importance_is_not_a_ten_point_budget(self):
        snapshot,p=self.fixture();original=copy.deepcopy((snapshot,p))
        value=goals(snapshot,p)
        self.assertEqual(value['ratings'],snapshot['goalRatings'])
        self.assertEqual(value['measurement_type'],'ordinal_importance_1_7')
        self.assertNotIn('points',value);self.assertNotIn('budget',value)
        self.assertEqual((snapshot,p),original)

    def test_mismatch_missing_and_bool_are_rejected(self):
        for change in ('mismatch','missing','bool'):
            s,p=self.fixture()
            if change=='mismatch':p['profile']['needs']['pro_env']=.5
            elif change=='missing':s['goalRatings'].pop('env')
            else:s['goalRatings']['env']=True
            with self.assertRaises(ChoiceEvidenceError):goals(s,p)

    def test_legacy_allocation_stays_legacy(self):
        points={k:0 for k in GOAL_MAP};points['env']=10
        s={'goalPoints':points,'conversion':'points_div10_round_to_1_7_v1','needsSource':'weekly_goal_allocation'}
        p={'observed_measurements':{'needs_raw_1_to_7':{n:7 if k=='env' else 1 for k,n in GOAL_MAP.items()}}}
        value=goals(s,p);self.assertEqual(value['budget'],10);self.assertEqual(value['points'],points)

if __name__=='__main__':unittest.main()
