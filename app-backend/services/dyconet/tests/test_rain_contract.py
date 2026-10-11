import unittest
from orion_context import OrionEntity
from orion_query_list import OrionQueryListProvider


class RainContractTest(unittest.TestCase):
    def entity(self, **attributes):
        return OrionEntity.from_mapping({'id': 'Sensor:Weather:Winfred', 'type': 'Weather', **attributes})

    def test_intensity_and_counter_are_distinct_and_raw_values_preserved(self):
        entity = self.entity(rainIntensity={'value': 6}, cumulativeRainfall={'value': 110.49}, uplinkInterval={'value': 60})
        rain, counter = OrionQueryListProvider().observations(entity, variables=('rain', 'cumulativeRainfall'))
        self.assertEqual((rain.numeric_value, rain.unit), (6, 'mm/h'))
        self.assertEqual(rain.source_metadata['measurement_window_minutes'], 10)
        self.assertEqual(rain.source_metadata['reporting_interval_minutes'], 60)
        self.assertIsNone(rain.source_metadata['last_report_at'])
        self.assertEqual((counter.numeric_value, counter.unit), (110.49, 'mm'))
        self.assertFalse(counter.source_metadata['normalization_eligible'])
        self.assertFalse(counter.source_metadata['rain_display_eligible'])
        self.assertEqual(entity.attributes['rainIntensity'], {'value': 6})

    def test_missing_and_invalid_intensity_never_falls_back_to_counter(self):
        for attributes in ({'cumulativeRainfall': {'value': 20}}, {'rainIntensity': {'value': -1}},
                           {'rainIntensity': {'value': 500}}, {'rainIntensity': {'value': 6, 'unit': 'mm'}}):
            rain = OrionQueryListProvider().observations(self.entity(**attributes), variables=('rain',))[0]
            self.assertFalse(rain.source_metadata['rain_display_eligible'])

    def test_report_time_requires_explicit_timezone_and_is_not_modification(self):
        for metadata, expected in (({'dateModified': {'value': '2026-10-11T09:00:00Z'}}, None),
                                   ({'receivedAt': {'value': '2026-10-11T09:00:00Z'}}, '2026-10-11T09:00:00+00:00'),
                                   ({'receivedAt': {'value': '2026-10-11 09:00:00'}}, None)):
            rain = OrionQueryListProvider().observations(self.entity(rainIntensity={'value': 0, 'metadata': metadata}), variables=('rain',))[0]
            self.assertEqual(rain.source_metadata['last_report_at'], expected)


if __name__ == '__main__':
    unittest.main()
