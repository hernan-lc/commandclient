#!/usr/bin/env python3
"""Exercise rejection of corrupt/mislabelled release artifacts, using built JARs."""
import sys
sys.dont_write_bytecode = True
import importlib.util, pathlib, tempfile, unittest, zipfile, contextlib, io
spec=importlib.util.spec_from_file_location('verify_plugin', pathlib.Path(__file__).with_name('verify-plugin.py'))
verifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(verifier)
artifacts=[pathlib.Path(path) for path in sys.argv[1:]]

class PackagingTests(unittest.TestCase):
    def altered(self, source, change, name=None):
        directory=tempfile.TemporaryDirectory();self.addCleanup(directory.cleanup)
        path=pathlib.Path(directory.name)/(name or source.name)
        with zipfile.ZipFile(source) as original, zipfile.ZipFile(path,'w') as modified:
            for info in original.infolist(): modified.writestr(info, change(info.filename,original.read(info.filename)))
        return path
    def test_both_baselines(self):
        self.assertEqual(2,len(artifacts))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual({'legacy','modern'}, {verifier.verify(path) for path in artifacts})
    def test_rejects_wrong_api(self):
        path=self.altered(artifacts[0], lambda name,data: data.replace(b"api-version: '1.16'",b"api-version: '1.21'") if name=='plugin.yml' else data)
        with self.assertRaises(AssertionError):verifier.verify(path)
    def test_rejects_newer_common_bytecode_in_legacy(self):
        path=self.altered(artifacts[0],lambda name,data: data[:6]+(65).to_bytes(2,'big')+data[8:] if name=='com/commandapi/api/CommandScheduler.class' else data)
        with self.assertRaises(AssertionError):verifier.verify(path)
    def test_rejects_legacy_contents_mislabelled_as_modern(self):
        path=self.altered(artifacts[0],lambda name,data:data,artifacts[1].name)
        with self.assertRaises(AssertionError):verifier.verify(path)

if __name__=='__main__': unittest.main(argv=[sys.argv[0]])
