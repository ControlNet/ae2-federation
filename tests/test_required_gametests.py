import unittest

from tools.required_gametests import required_test_ids, select_shard, verify_one


class RequiredGameTestsTest(unittest.TestCase):
    def test_release_shards_cover_manifest_exactly_once(self):
        tests = required_test_ids()
        partitions = [select_shard(tests, index, 8) for index in range(8)]
        combined = [test for partition in partitions for test in partition]
        self.assertCountEqual(combined, tests)
        self.assertEqual(len(combined), len(set(combined)))
        self.assertTrue(all(partitions))

    def test_default_shard_preserves_full_suite(self):
        tests = required_test_ids()
        self.assertEqual(select_shard(tests, 0, 1), tests)

    def test_invalid_shards_are_rejected(self):
        tests = required_test_ids()
        for index, count in ((-1, 8), (8, 8), (0, 0), (0, len(tests) + 1)):
            with self.subTest(index=index, count=count), self.assertRaises(ValueError):
                select_shard(tests, index, count)

    def test_completion_requires_one_successful_test(self):
        verify_one("harnessnativesmoke", "All 1 required tests passed :)", 0)
        for output, code in (("All 2 required tests passed :)", 0),
                             ("All 1 required tests passed :)", 1), ("", 0)):
            with self.subTest(output=output, code=code), self.assertRaises(ValueError):
                verify_one("harnessnativesmoke", output, code)
