"""Resource-level regression checks, runnable without an Android SDK."""
import collections
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/res'
FORMAT = re.compile(r'%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]')


def resources(folder):
    result = {}
    for file in sorted((RES / folder).glob('*.xml')):
        for node in ET.parse(file).getroot():
            if node.tag not in ('string', 'plurals', 'string-array') or node.get('translatable') == 'false':
                continue
            key = node.get('name')
            if key in result:
                raise AssertionError(f'Duplicate resource: {folder}/{key}')
            result[key] = node
    return result


def text(node):
    return ''.join(node.itertext()).strip()


def signature(value):
    return collections.Counter(FORMAT.findall(value))


class KoreanResourcesTest(unittest.TestCase):
    def test_canonical_korean_covers_all_default_keys(self):
        self.assertEqual(set(resources('values')), set(resources('values-ko')))

    def test_korean_types_and_format_arguments_match(self):
        english, korean = resources('values'), resources('values-ko')
        for key in english.keys() & korean.keys():
            en, ko = english[key], korean[key]
            with self.subTest(key=key):
                self.assertEqual(en.tag, ko.tag)
                if en.tag == 'plurals':
                    source = en.find("item[@quantity='other']")
                    self.assertIsNotNone(ko.find("item[@quantity='other']"))
                    for item in ko:
                        matching = en.find(f"item[@quantity='{item.get('quantity')}']")
                        expected = matching if matching is not None else source
                        self.assertEqual(signature(text(expected)), signature(text(item)))
                else:
                    self.assertEqual(signature(text(en)), signature(text(ko)))

    def test_regional_resources_do_not_hide_korean_translations(self):
        korean = resources('values-ko')
        for key, regional in resources('values-ko-rKR').items():
            if key in korean and re.search('[가-힣]', text(korean[key])):
                with self.subTest(key=key):
                    self.assertRegex(text(regional), '[가-힣]')

    def test_korean_is_selectable(self):
        tags = ET.parse(RES / 'values/app_language_tags.xml').getroot()
        self.assertIn('ko', [i.text for i in tags.findall('./string-array/item')])


if __name__ == '__main__':
    unittest.main()
