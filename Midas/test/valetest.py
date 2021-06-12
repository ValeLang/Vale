import unittest
import subprocess
import platform
import os.path
import os
import sys
import shutil
import glob

from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    # print("Running: " + " ".join(args))
    return subprocess.run(args, capture_output=True, text=True, **kwargs)


PATH_TO_SAMPLES = "../Valestrom/Tests/test/main/resources/"

class ValeTest(unittest.TestCase):
    GENPATH: str = os.environ.get('GENPATH', ".")

    def valec(self,
              module_name: str,
              in_filepaths: List[str],
              o_files_dir: str,
              exe_name: str,
              region_override: str,
              extra_flags: List[str]) -> subprocess.CompletedProcess:
        assert self.GENPATH
        python = "python" if self.windows else "python3"
        return procrun(
            [python,
             f"{self.GENPATH}/valec.py",
             "build",
             module_name,
             "--verify",
             "--llvmir",
             "--census",
             "--flares",
             "--region-override", region_override,
             "--output-dir", o_files_dir,
             "--add-exports-include-path",
             "-o",
             exe_name] + extra_flags + in_filepaths)

    def exec(self, exe_file: str) -> subprocess.CompletedProcess:
        return procrun([f"./{exe_file}"])

    @classmethod
    def setUpClass(cls) -> None:
        print(
            f"Using valec from {cls.GENPATH}. " +
            "Set GENPATH env var if this is incorrect",
            file=sys.stderr)

    def setUp(self) -> None:
        self.GENPATH: str = type(self).GENPATH
        self.windows = platform.system() == 'Windows'

    def compile_and_execute(
            self,
            in_filepaths: List[str],
            region_override: str,
            extra_flags: List[str]) -> subprocess.CompletedProcess:
        first_vale_filepath = in_filepaths[0]
        file_name_without_extension = os.path.splitext(os.path.basename(first_vale_filepath))[0]
        build_dir = f"test/test_build/{file_name_without_extension}_build"

        module_name = "tmod"
        for i in range(0, len(in_filepaths)):
            in_filepaths[i] = module_name + ":" + in_filepaths[i]

        proc = self.valec(module_name, in_filepaths, build_dir, file_name_without_extension, region_override, extra_flags)
        self.assertEqual(proc.returncode, 0,
                         f"valec couldn't compile {in_filepaths}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        exe_file = f"{build_dir}/{file_name_without_extension}"

        proc = self.exec(exe_file)
        return proc

    def compile_and_execute_and_expect_return_code(
            self,
            vale_files: List[str],
            region_override: str,
            expected_return_code: int,
            extra_flags: List[str] = None) -> None:
        if extra_flags is None:
            extra_flags = []
        proc = self.compile_and_execute(vale_files, region_override, extra_flags)
        # print(proc.stdout)
        # print(proc.stderr)
        if proc.returncode != expected_return_code:
            first_vale_filepath = vale_files[0]
            file_name_without_extension = os.path.splitext(os.path.basename(first_vale_filepath))[0]
            build_dir = f"test/test_build/{file_name_without_extension}_build"
            textfile = open(build_dir + "/stdout.txt", "w")
            a = textfile.write(proc.stdout)
            textfile.close()
            textfile = open(build_dir + "/stderr.txt", "w")
            a = textfile.write(proc.stderr)
            textfile.close()
            self.assertEqual(proc.returncode, expected_return_code,
                             f"Unexpected result: {proc.returncode}\n" + proc.stdout + proc.stderr)

    # Tests for immutables in exports/externs
    def test_assist_externstrlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externstrlen"], "assist", 11)
    def test_assist_externretvoid(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externretvoid"], "assist", 42)
    def test_assist_externimmstructparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externimmstructparam"], "assist", 42)
    def test_assist_externimmstructparamdeep(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externimmstructparamdeep"], "assist", 42)
    def test_assist_externimminterfaceparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externimminterfaceparam"], "assist", 42)
    def test_assist_externimmrsaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externimmrsaparam"], "assist", 10)
    def test_assist_externimmrsaparamdeep(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externimmrsaparamdeep"], "assist", 20)
    def test_assist_exportretvoid(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportretvoid"], "assist", 42)
    def test_assist_exportretstr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportretstr"], "assist", 6)
    def test_assist_exportimmstructparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportimmstructparam"], "assist", 42)
    def test_assist_exportimmstructparamdeep(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportimmstructparamdeep"], "assist", 42)
    def test_assist_exportimminterfaceparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportimminterfaceparam"], "assist", 42)
    def test_assist_exportimmrsaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportimmrsaparam"], "assist", 10)
    def test_assist_smallstr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/smallstr.vale"], "assist", 42)
    def test_assist_immtupleaccess(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/tuples/immtupleaccess.vale"], "assist", 42)
    def test_assist_immssafromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immssafromcallable.vale"], "assist", 42)
    def test_assist_immssafromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immssafromvalues.vale"], "assist", 42)

    # kldc = known live double check
    def test_resilientv3_kldc(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/deadmutstruct.vale"], "resilient-v3", 116, ["--override-known-live-true"])

    def test_twinpages(self) -> None:
        proc = procrun(["clang", "test/testtwinpages.c", "-o", "test/test_build/testtwinpages"])
        self.assertEqual(proc.returncode, 0, f"Twin pages test failed!")

    def test_assist_addret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/addret.vale"], "assist", 7)
    def test_assist_add64ret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/add64ret.vale"], "assist", 42)
    def test_assist_floatarithmetic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/floatarithmetic.vale"], "assist", 42)
    def test_assist_floateq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/floateq.vale"], "assist", 42)
    def test_assist_concatstrfloat(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/concatstrfloat.vale"], "assist", 42)

    # def test_resilientv4_tether(self) -> None:
    #     self.compile_and_execute_and_expect_return_code(["test/tether.vale"], "resilient-v4", 0)

    def test_assist_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "assist", 42)
    def test_unsafefast_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutswaplocals(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "resilient-v4", 42)
    def test_resilientv3_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "resilient-v3", 42)
    def test_naiverc_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "naive-rc", 42)

    def test_assist_immstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/immstruct.vale"], "assist", 5)
    def test_unsafefast_immstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/immstruct.vale"], "unsafe-fast", 5)
    # def test_resilientv4_immstruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/immstruct.vale"], "resilient-v4", 5)
    def test_resilientv3_immstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/immstruct.vale"], "resilient-v3", 5)
    def test_naiverc_immstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/immstruct.vale"], "naive-rc", 5)

    def test_assist_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "assist", 5)
    def test_unsafefast_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "unsafe-fast", 5)
    # def test_resilientv4_memberrefcount(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "resilient-v4", 5)
    def test_resilientv3_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "resilient-v3", 5)
    def test_naiverc_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "naive-rc", 5)

    def test_assist_bigimmstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigimmstruct.vale"], "assist", 42)
    def test_unsafefast_bigimmstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigimmstruct.vale"], "unsafe-fast", 42)
    # def test_resilientv4_bigimmstruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigimmstruct.vale"], "resilient-v4", 42)
    def test_resilientv3_bigimmstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigimmstruct.vale"], "resilient-v3", 42)
    def test_naiverc_bigimmstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigimmstruct.vale"], "naive-rc", 42)

    def test_assist_mutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstruct.vale"], "assist", 8)
    def test_unsafefast_mutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstruct.vale"], "unsafe-fast", 8)
    # def test_resilientv4_mutstruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstruct.vale"], "resilient-v4", 8)
    def test_resilientv3_mutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstruct.vale"], "resilient-v3", 8)
    def test_naiverc_mutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstruct.vale"], "naive-rc", 8)

    def test_assist_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "assist", 42)
    def test_unsafefast_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "unsafe-fast", 42)
    # def test_resilientv4_lambda(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "resilient-v4", 42)
    def test_resilientv3_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "resilient-v3", 42)
    def test_naiverc_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "naive-rc", 42)

    def test_assist_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "assist", 42)
    def test_unsafefast_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "unsafe-fast", 42)
    # def test_resilientv4_if(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "resilient-v4", 42)
    def test_resilientv3_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "resilient-v3", 42)
    def test_naiverc_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "naive-rc", 42)

    def test_assist_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "assist", 42)
    def test_unsafefast_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "unsafe-fast", 42)
    # def test_resilientv4_upcastif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "resilient-v4", 42)
    def test_resilientv3_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "resilient-v3", 42)
    def test_naiverc_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "naive-rc", 42)

    def test_assist_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "assist", 42)
    def test_unsafefast_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "unsafe-fast", 42)
    # def test_resilientv4_ifnevers(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "resilient-v4", 42)
    def test_resilientv3_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "resilient-v3", 42)
    def test_naiverc_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "naive-rc", 42)

    def test_assist_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "assist", 42)
    def test_unsafefast_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutlocal(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "resilient-v4", 42)
    def test_resilientv3_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "resilient-v3", 42)
    def test_naiverc_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "naive-rc", 42)

    def test_assist_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "assist", 42)
    def test_unsafefast_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "unsafe-fast", 42)
    # def test_resilientv4_while(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "resilient-v4", 42)
    def test_resilientv3_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "resilient-v3", 42)
    def test_naiverc_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "naive-rc", 42)

    def test_assist_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "assist", 8)
    def test_unsafefast_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "unsafe-fast", 8)
    # def test_resilientv4_constraintRef(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "resilient-v4", 8)
    def test_resilientv3_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "resilient-v3", 8)
    def test_naiverc_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "naive-rc", 8)

    def test_assist_mutssafromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromcallable.vale"], "assist", 42)
    def test_unsafefast_mutssafromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromcallable.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutssafromcallable(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromcallable.vale"], "resilient-v4", 42)
    def test_resilientv3_mutssafromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromcallable.vale"], "resilient-v3", 42)
    def test_naiverc_mutssafromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromcallable.vale"], "naive-rc", 42)

    def test_assist_mutssafromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromvalues.vale"], "assist", 42)
    def test_unsafefast_mutssafromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromvalues.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutssafromvalues(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromvalues.vale"], "resilient-v4", 42)
    def test_resilientv3_mutssafromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromvalues.vale"], "resilient-v3", 42)
    def test_naiverc_mutssafromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutssafromvalues.vale"], "naive-rc", 42)

    def test_assist_imminterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/imminterface.vale"], "assist", 42)
    def test_unsafefast_imminterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/imminterface.vale"], "unsafe-fast", 42)
    # def test_resilientv4_imminterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/imminterface.vale"], "resilient-v4", 42)
    def test_resilientv3_imminterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/imminterface.vale"], "resilient-v3", 42)
    def test_naiverc_imminterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/imminterface.vale"], "naive-rc", 42)

    def test_assist_mutinterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/mutinterface.vale"], "assist", 42)
    def test_unsafefast_mutinterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/mutinterface.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutinterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/mutinterface.vale"], "resilient-v4", 42)
    def test_resilientv3_mutinterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/mutinterface.vale"], "resilient-v3", 42)
    def test_naiverc_mutinterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/mutinterface.vale"], "naive-rc", 42)

    def test_assist_mutstructstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstore.vale"], "assist", 42)
    def test_unsafefast_mutstructstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstore.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutstructstore(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstore.vale"], "resilient-v4", 42)
    def test_resilientv3_mutstructstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstore.vale"], "resilient-v3", 42)
    def test_naiverc_mutstructstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstore.vale"], "naive-rc", 42)

    def test_assist_mutstructstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstoreinner.vale"], "assist", 42)
    def test_unsafefast_mutstructstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstoreinner.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutstructstoreinner(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstoreinner.vale"], "resilient-v4", 42)
    def test_resilientv3_mutstructstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstoreinner.vale"], "resilient-v3", 42)
    def test_naiverc_mutstructstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/mutstructstoreinner.vale"], "naive-rc", 42)

    def test_assist_immrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immrsa.vale"], "assist", 3)
    def test_unsafefast_immrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immrsa.vale"], "unsafe-fast", 3)
    # def test_resilientv4_immrsa(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immrsa.vale"], "resilient-v4", 3)
    def test_resilientv3_immrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immrsa.vale"], "resilient-v3", 3)
    def test_naiverc_immrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/immrsa.vale"], "naive-rc", 3)

    def test_assist_mutrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsa.vale"], "assist", 3)
    def test_unsafefast_mutrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsa.vale"], "unsafe-fast", 3)
    # def test_resilientv4_mutrsa(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsa.vale"], "resilient-v4", 3)
    def test_resilientv3_mutrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsa.vale"], "resilient-v3", 3)
    def test_naiverc_mutrsa(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsa.vale"], "naive-rc", 3)

    def test_assist_exportmutrsaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutrsaparam"], "assist", 10)
    def test_unsafefast_exportmutrsaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutrsaparam"], "unsafe-fast", 10)
    # def test_resilientv4_exportmutrsaparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutrsaparam"], "resilient-v4", 10)
    def test_resilientv3_exportmutrsaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutrsaparam"], "resilient-v3", 10)
    def test_naiverc_exportmutrsaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutrsaparam"], "naive-rc", 10)

    def test_assist_exportmutssaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutssaparam"], "assist", 10)
    def test_unsafefast_exportmutssaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutssaparam"], "unsafe-fast", 10)
    # def test_resilientv4_exportmutssaparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutssaparam"], "resilient-v4", 10)
    def test_resilientv3_exportmutssaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutssaparam"], "resilient-v3", 10)
    def test_naiverc_exportmutssaparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/exportmutssaparam"], "naive-rc", 10)

    def test_assist_mutrsalen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsalen.vale"], "assist", 5)
    def test_unsafefast_mutrsalen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsalen.vale"], "unsafe-fast", 5)
    # def test_resilientv4_mutrsalen(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsalen.vale"], "resilient-v4", 5)
    def test_resilientv3_mutrsalen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsalen.vale"], "resilient-v3", 5)
    def test_naiverc_mutrsalen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/mutrsalen.vale"], "naive-rc", 5)

    def test_assist_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "assist", 42)
    def test_unsafefast_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "unsafe-fast", 42)
    # def test_resilientv4_stradd(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "resilient-v4", 42)
    def test_resilientv3_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "resilient-v3", 42)
    def test_naiverc_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "naive-rc", 42)

    def test_assist_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "assist", 42)
    def test_unsafefast_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "unsafe-fast", 42)
    # def test_resilientv4_strneq(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "resilient-v4", 42)
    def test_resilientv3_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "resilient-v3", 42)
    def test_naiverc_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "naive-rc", 42)

    def test_assist_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "assist", 42)
    def test_unsafefast_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "unsafe-fast", 42)
    # def test_resilientv4_lambdamut(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "resilient-v4", 42)
    def test_resilientv3_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "resilient-v3", 42)
    def test_naiverc_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "naive-rc", 42)

    def test_assist_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "assist", 42)
    def test_unsafefast_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "unsafe-fast", 42)
    # def test_resilientv4_strprint(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "resilient-v4", 42)
    def test_resilientv3_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "resilient-v3", 42)
    def test_naiverc_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "naive-rc", 42)

    def test_assist_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "assist", 4)
    def test_unsafefast_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "unsafe-fast", 4)
    # def test_resilientv4_inttostr(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "resilient-v4", 4)
    def test_resilientv3_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "resilient-v3", 4)
    def test_naiverc_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "naive-rc", 4)

    def test_assist_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "assist", 42)
    def test_unsafefast_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "unsafe-fast", 42)
    # def test_resilientv4_nestedif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "resilient-v4", 42)
    def test_resilientv3_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "resilient-v3", 42)
    def test_naiverc_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "naive-rc", 42)

    def test_assist_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "assist", 42)
    def test_unsafefast_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "unsafe-fast", 42)
    # def test_resilientv4_unstackifyret(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "resilient-v4", 42)
    def test_resilientv3_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "resilient-v3", 42)
    def test_naiverc_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "naive-rc", 42)

    def test_assist_swapmutrsadestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swapmutrsadestroy.vale"], "assist", 42)
    def test_unsafefast_swapmutrsadestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swapmutrsadestroy.vale"], "unsafe-fast", 42)
    # def test_resilientv4_swapmutrsadestroy(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swapmutrsadestroy.vale"], "resilient-v4", 42)
    def test_resilientv3_swapmutrsadestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swapmutrsadestroy.vale"], "resilient-v3", 42)
    def test_naiverc_swapmutrsadestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swapmutrsadestroy.vale"], "naive-rc", 42)

    def test_assist_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "assist", 42)
    def test_unsafefast_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastConstraintSuccessful(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "resilient-v3", 42)
    def test_naiverc_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "naive-rc", 42)

    def test_assist_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "assist", 42)
    def test_unsafefast_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastConstraintFailed(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "resilient-v3", 42)
    def test_naiverc_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "naive-rc", 42)

    def test_assist_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "assist", 42)
    def test_unsafefast_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastOwningSuccessful(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "resilient-v3", 42)
    def test_naiverc_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "naive-rc", 42)

    def test_assist_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "assist", 42)
    def test_unsafefast_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastOwningFailed(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "resilient-v3", 42)
    def test_naiverc_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "naive-rc", 42)

    def test_assist_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "assist", 42)
    def test_unsafefast_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "unsafe-fast", 42)
    # def test_resilientv4_unreachablemoot(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "resilient-v4", 42)
    def test_resilientv3_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "resilient-v3", 42)
    def test_naiverc_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "naive-rc", 42)

    def test_assist_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "assist", 1)
    def test_unsafefast_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "unsafe-fast", 1)
    # def test_resilientv4_panic(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "resilient-v4", 1)
    def test_resilientv3_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "resilient-v3", 1)
    def test_naiverc_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "naive-rc", 1)

    def test_assist_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "assist", 42)
    def test_unsafefast_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "unsafe-fast", 42)
    # def test_resilientv4_panicnot(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "resilient-v4", 42)
    def test_resilientv3_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "resilient-v3", 42)
    def test_naiverc_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "naive-rc", 42)

    def test_assist_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "assist", 42)
    def test_unsafefast_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "unsafe-fast", 42)
    # def test_resilientv4_nestedblocks(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "resilient-v4", 42)
    def test_resilientv3_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "resilient-v3", 42)
    def test_naiverc_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "naive-rc", 42)

    def test_assist_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "assist", 42)
    def test_unsafefast_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "unsafe-fast", 42)
    # def test_resilientv4_weakDropThenLockStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "resilient-v4", 42)
    def test_resilientv3_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "resilient-v3", 42)
    def test_naiverc_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "naive-rc", 42)

    def test_assist_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "assist", 7)
    def test_unsafefast_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakLockWhileLiveStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "resilient-v4", 7)
    def test_resilientv3_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "resilient-v3", 7)
    def test_naiverc_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "naive-rc", 7)

    def test_assist_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "assist", 7)
    def test_unsafefast_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromLocalCRefStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "naive-rc", 7)

    def test_assist_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "assist", 7)
    def test_unsafefast_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromCRefStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "naive-rc", 7)

    def test_assist_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "assist", 7)
    def test_unsafefast_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "unsafe-fast", 7)
    # def test_resilientv4_loadFromWeakable(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "resilient-v4", 7)
    def test_resilientv3_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "resilient-v3", 7)
    def test_naiverc_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "naive-rc", 7)

    def test_assist_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "assist", 42)
    def test_unsafefast_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "unsafe-fast", 42)
    # def test_resilientv4_weakDropThenLockInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "resilient-v4", 42)
    def test_resilientv3_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "resilient-v3", 42)
    def test_naiverc_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "naive-rc", 42)

    def test_assist_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "assist", 7)
    def test_unsafefast_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakLockWhileLiveInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "resilient-v4", 7)
    def test_resilientv3_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "resilient-v3", 7)
    def test_naiverc_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "naive-rc", 7)

    def test_assist_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "assist", 7)
    def test_unsafefast_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromLocalCRefInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "naive-rc", 7)

    def test_assist_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "assist", 7)
    def test_unsafefast_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromCRefInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "naive-rc", 7)

    def test_assist_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "assist", 42)
    def test_unsafefast_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "unsafe-fast", 42)
    # def test_resilientv4_weakSelfMethodCallWhileLive(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "resilient-v4", 42)
    def test_resilientv3_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "resilient-v3", 42)
    def test_naiverc_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "naive-rc", 42)

    def test_assist_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "assist", 0)
    def test_unsafefast_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "unsafe-fast", 0)
    # def test_resilientv4_weakSelfMethodCallAfterDrop(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "resilient-v4", 0)
    def test_resilientv3_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "resilient-v3", 0)
    def test_naiverc_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "naive-rc", 0)

    # def test_assist_externtupleret(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleret"], "assist", 42)
    # def test_unsafefast_externtupleret(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleret"], "unsafe-fast", 42)
    # # def test_resilientv4_externtupleret(self) -> None:
    # #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleret"], "resilient-v4", 42)
    # def test_resilientv3_externtupleret(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleret"], "resilient-v3", 42)
    # def test_naiverc_externtupleret(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleret"], "naive-rc", 42)

    def test_assist_externstructparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externstructparam"], "assist", 42)
    # def test_unsafefast_externstructparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externstructparam"], "unsafe-fast", 42)
    # def test_resilientv4_externstructparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externstructparam"], "resilient-v4", 42)
    def test_resilientv3_externstructparam(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externstructparam"], "resilient-v3", 42)
    # def test_naiverc_externstructparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externstructparam"], "naive-rc", 42)

    def test_assist_externretmutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externretmutstruct"], "assist", 42)
    def test_unsafefast_externretmutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externretmutstruct"], "unsafe-fast", 42)
    # def test_resilientv4_externretmutstruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externretmutstruct"], "resilient-v4", 42)
    def test_resilientv3_externretmutstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externretmutstruct"], "resilient-v3", 42)
    # def test_naiverc_externretmutstruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externretmutstruct"], "naive-rc", 42)

    def test_assist_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "assist", 12)
    def test_unsafefast_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "unsafe-fast", 12)
    # def test_resilientv4_strlen(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "resilient-v4", 12)
    def test_resilientv3_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "resilient-v3", 12)
    def test_naiverc_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "naive-rc", 12)

    # Cant get an invalid access in assist mode, a constraint ref catches it first
    def test_unsafefast_invalidaccess(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "unsafe-fast", 14)
    # def test_resilientv4_invalidaccess(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "resilient-v4", -11)
    def test_resilientv3_invalidaccess(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "resilient-v3", -11)
    # def test_naiverc_invalidaccess(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "naive-rc", 255)

    # def test_assist_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "assist", 42)
    # def test_unsafefast_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "unsafe-fast", 42)
    # # def test_resilientv4_neverif(self) -> None:
    # #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "resilient-v4", 42)
    # def test_resilientv3_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "resilient-v3", 42)
    # def test_naiverc_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "naive-rc", 42)

    # def test_assist_externtupleparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleparam"], "assist", 42)
    # def test_unsafefast_externtupleparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleparam"], "unsafe-fast", 42)
    # # def test_resilientv4_externtupleparam(self) -> None:
    # #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleparam"], "resilient-v4", 42)
    # def test_resilientv3_externtupleparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleparam"], "resilient-v3", 42)
    # def test_naiverc_externtupleparam(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/externtupleparam"], "naive-rc", 42)


if __name__ == '__main__':
    unittest.main()
