import unittest
import subprocess
import os.path
import os
import sys
import shutil
import glob

from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, **kwargs)


PATH_TO_SAMPLES = "../Valestrom/Samples/test/main/resources/"

class ValeTest(unittest.TestCase):
    GENPATH: str = os.environ.get('GENPATH', "cmake-build-debug")

    def valestrom(self, vale_filepaths: List[str],
                  vir_file: str) -> subprocess.CompletedProcess:
        driver = "test/Driver.jar"
        driver_class = "net.verdagon.vale.driver.Driver"
        return procrun(
            [
                "java",
                "-cp",
                driver,
                driver_class,
                "build",
                "-o",
                vir_file
            ] +
            vale_filepaths
        )

    def valec(self, vir_file: str,
              o_files_dir: str,
              region_override: str) -> subprocess.CompletedProcess:
        assert self.GENPATH
        return procrun(
            [f"{self.GENPATH}/valec", "--verify", "--llvmir", "--census", "--region-override", region_override, "--output-dir",
             o_files_dir, vir_file])

    def clang(self, o_files: List[str],
              exe_file: str) -> subprocess.CompletedProcess:
        return procrun(["clang", "-o", exe_file] + o_files)

    def exec(self, exe_file: str) -> subprocess.CompletedProcess:
        return procrun([f"./{exe_file}"])

    @classmethod
    def setUpClass(cls) -> None:
        print(
            f"Using valec from {cls.GENPATH}. " +
            "Set GENPATH env var if this is incorrect",
            file=sys.stderr
        )

    def setUp(self) -> None:
        self.GENPATH: str = type(self).GENPATH

    def compile_and_execute(
            self, vale_filepaths: List[str], region_override: str) -> subprocess.CompletedProcess:
        last_vale_filepath = vale_filepaths[len(vale_filepaths) - 1]
        file_name_without_extension = os.path.splitext(os.path.basename(last_vale_filepath))[0]
        build_dir = f"test/test_build/{file_name_without_extension}_build"

        if os.path.exists(build_dir):
            shutil.rmtree(build_dir)
        os.makedirs(build_dir)

        vir_file = f"{build_dir}/{file_name_without_extension}.vir"
        proc = self.valestrom(vale_filepaths, vir_file)
        # print(proc.stdout)
        # print(proc.stderr)
        self.assertEqual(proc.returncode, 0,
                         f"valestrom couldn't compile {file_name_without_extension}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        proc = self.valec(vir_file, build_dir, region_override)
        self.assertEqual(proc.returncode, 0,
                         f"valec couldn't compile {vir_file}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        exe_file = f"{build_dir}/{file_name_without_extension}"
        o_files = glob.glob(f"{build_dir}/*.o") + ["src/valestd/assert.c", "src/valestd/stdio.c", "src/valestd/str.c", "src/valestd/census.c", "src/valestd/weaks.c"]
        proc = self.clang(o_files, exe_file)
        self.assertEqual(proc.returncode, 0,
                         f"clang couldn't compile {o_files}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        proc = self.exec(exe_file)
        return proc

    def compile_and_execute_and_expect_return_code(
        self, vale_files: List[str], region_override: str, expected_return_code) -> None:
        proc = self.compile_and_execute(vale_files, region_override)
        # print(proc.stdout)
        # print(proc.stderr)
        self.assertEqual(proc.returncode, expected_return_code,
                         f"Unexpected result: {proc.returncode}\n" + proc.stdout + proc.stderr)

    def test_addret_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "addret.vale"], "assist", 7)
    def test_addret_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "addret.vale"], "unsafe-fast", 7)
    def test_addret_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "addret.vale"], "resilient-v0", 7)
    def test_addret_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "addret.vale"], "resilient-v1", 7)

    def test_immstruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/immstruct.vale"], "assist", 5)
    def test_immstruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/immstruct.vale"], "unsafe-fast", 5)
    def test_immstruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/immstruct.vale"], "resilient-v0", 5)
    def test_immstruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/immstruct.vale"], "resilient-v1", 5)

    def test_memberrefcount_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/memberrefcount.vale"], "assist", 5)
    def test_memberrefcount_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/memberrefcount.vale"], "unsafe-fast", 5)
    def test_memberrefcount_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/memberrefcount.vale"], "resilient-v0", 5)
    def test_memberrefcount_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/memberrefcount.vale"], "resilient-v1", 5)

    def test_bigimmstruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/bigimmstruct.vale"], "assist", 42)
    def test_bigimmstruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/bigimmstruct.vale"], "unsafe-fast", 42)
    def test_bigimmstruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/bigimmstruct.vale"], "resilient-v0", 42)
    def test_bigimmstruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/bigimmstruct.vale"], "resilient-v1", 42)

    def test_mutstruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstruct.vale"], "assist", 8)
    def test_mutstruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstruct.vale"], "unsafe-fast", 8)
    def test_mutstruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstruct.vale"], "resilient-v0", 8)
    def test_mutstruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstruct.vale"], "resilient-v1", 8)

    def test_lambda_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambda.vale"], "assist", 42)
    def test_lambda_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambda.vale"], "unsafe-fast", 42)
    def test_lambda_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambda.vale"], "resilient-v0", 42)
    def test_lambda_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambda.vale"], "resilient-v1", 42)

    def test_if_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/if.vale"], "assist", 42)
    def test_if_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/if.vale"], "unsafe-fast", 42)
    def test_if_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/if.vale"], "resilient-v0", 42)
    def test_if_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/if.vale"], "resilient-v1", 42)

    def test_mutlocal_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "mutlocal.vale"], "assist", 42)
    def test_mutlocal_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "mutlocal.vale"], "unsafe-fast", 42)
    def test_mutlocal_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "mutlocal.vale"], "resilient-v0", 42)
    def test_mutlocal_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "mutlocal.vale"], "resilient-v1", 42)

    def test_while_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "while/while.vale"], "assist", 42)
    def test_while_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "while/while.vale"], "unsafe-fast", 42)
    def test_while_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "while/while.vale"], "resilient-v0", 42)
    def test_while_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "while/while.vale"], "resilient-v1", 42)

    def test_constraintRef_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "constraintRef.vale"], "assist", 8)
    def test_constraintRef_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "constraintRef.vale"], "unsafe-fast", 8)
    def test_constraintRef_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "constraintRef.vale"], "resilient-v0", 8)
    def test_constraintRef_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "constraintRef.vale"], "resilient-v1", 8)

    def test_knownsizeimmarray_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/knownsizeimmarray.vale"], "assist", 42)
    def test_knownsizeimmarray_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/knownsizeimmarray.vale"], "unsafe-fast", 42)
    def test_knownsizeimmarray_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/knownsizeimmarray.vale"], "resilient-v0", 42)
    def test_knownsizeimmarray_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/knownsizeimmarray.vale"], "resilient-v1", 42)

    def test_imminterface_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/imminterface.vale"], "assist", 42)
    def test_imminterface_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/imminterface.vale"], "unsafe-fast", 42)
    def test_imminterface_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/imminterface.vale"], "resilient-v0", 42)
    def test_imminterface_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/imminterface.vale"], "resilient-v1", 42)

    def test_mutinterface_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/mutinterface.vale"], "assist", 42)
    def test_mutinterface_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/mutinterface.vale"], "unsafe-fast", 42)
    def test_mutinterface_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/mutinterface.vale"], "resilient-v0", 42)
    def test_mutinterface_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "virtuals/mutinterface.vale"], "resilient-v1", 42)

    def test_mutstructstore_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstructstore.vale"], "assist", 42)
    def test_mutstructstore_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstructstore.vale"], "unsafe-fast", 42)
    def test_mutstructstore_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstructstore.vale"], "resilient-v0", 42)
    def test_mutstructstore_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "structs/mutstructstore.vale"], "resilient-v1", 42)

    def test_immusa_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusa.vale"], "assist", 3)
    def test_immusa_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusa.vale"], "unsafe-fast", 3)
    def test_immusa_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusa.vale"], "resilient-v0", 3)
    def test_immusa_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusa.vale"], "resilient-v1", 3)

    def test_immusalen_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusalen.vale"], "assist", 5)
    def test_immusalen_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusalen.vale"], "unsafe-fast", 5)
    def test_immusalen_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusalen.vale"], "resilient-v0", 5)
    def test_immusalen_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/immusalen.vale"], "resilient-v1", 5)

    def test_mutusa_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusa.vale"], "assist", 3)
    def test_mutusa_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusa.vale"], "unsafe-fast", 3)
    def test_mutusa_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusa.vale"], "resilient-v0", 3)
    def test_mutusa_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusa.vale"], "resilient-v1", 3)

    def test_mutusalen_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusalen.vale"], "assist", 5)
    def test_mutusalen_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusalen.vale"], "unsafe-fast", 5)
    def test_mutusalen_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusalen.vale"], "resilient-v0", 5)
    def test_mutusalen_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/mutusalen.vale"], "resilient-v1", 5)

    def test_stradd_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/stradd.vale"], "assist", 42)
    def test_stradd_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/stradd.vale"], "unsafe-fast", 42)
    def test_stradd_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/stradd.vale"], "resilient-v0", 42)
    def test_stradd_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/stradd.vale"], "resilient-v1", 42)

    def test_lambdamut_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambdamut.vale"], "assist", 42)
    def test_lambdamut_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambdamut.vale"], "unsafe-fast", 42)
    def test_lambdamut_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambdamut.vale"], "resilient-v0", 42)
    def test_lambdamut_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "lambdas/lambdamut.vale"], "resilient-v1", 42)

    def test_strprint_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/strprint.vale"], "assist", 42)
    def test_strprint_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/strprint.vale"], "unsafe-fast", 42)
    def test_strprint_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/strprint.vale"], "resilient-v0", 42)
    def test_strprint_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/strprint.vale"], "resilient-v1", 42)

    def test_inttostr_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/inttostr.vale"], "assist", 42)
    def test_inttostr_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/inttostr.vale"], "unsafe-fast", 42)
    def test_inttostr_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/inttostr.vale"], "resilient-v0", 42)
    def test_inttostr_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "strings/inttostr.vale"], "resilient-v1", 42)

    def test_nestedif_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/nestedif.vale"], "assist", 42)
    def test_nestedif_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/nestedif.vale"], "unsafe-fast", 42)
    def test_nestedif_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/nestedif.vale"], "resilient-v0", 42)
    def test_nestedif_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "if/nestedif.vale"], "resilient-v1", 42)

    def test_unstackifyret_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unstackifyret.vale"], "assist", 42)
    def test_unstackifyret_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unstackifyret.vale"], "unsafe-fast", 42)
    def test_unstackifyret_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unstackifyret.vale"], "resilient-v0", 42)
    def test_unstackifyret_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unstackifyret.vale"], "resilient-v1", 42)

    def test_swapmutusadestroy_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/swapmutusadestroy.vale"], "assist", 42)
    def test_swapmutusadestroy_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/swapmutusadestroy.vale"], "unsafe-fast", 42)
    def test_swapmutusadestroy_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/swapmutusadestroy.vale"], "resilient-v0", 42)
    def test_swapmutusadestroy_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "arrays/swapmutusadestroy.vale"], "resilient-v1", 42)

    def test_unreachablemoot_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unreachablemoot.vale"], "assist", 42)
    def test_unreachablemoot_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unreachablemoot.vale"], "unsafe-fast", 42)
    def test_unreachablemoot_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unreachablemoot.vale"], "resilient-v0", 42)
    def test_unreachablemoot_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "unreachablemoot.vale"], "resilient-v1", 42)

    def test_panic_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "panic.vale"], "assist", 42)
    def test_panic_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "panic.vale"], "unsafe-fast", 42)
    def test_panic_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "panic.vale"], "resilient-v0", 42)
    def test_panic_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "panic.vale"], "resilient-v1", 42)

    def test_nestedblocks_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "nestedblocks.vale"], "assist", 42)
    def test_nestedblocks_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "nestedblocks.vale"], "unsafe-fast", 42)
    def test_nestedblocks_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "nestedblocks.vale"], "resilient-v0", 42)
    def test_nestedblocks_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "nestedblocks.vale"], "resilient-v1", 42)

    def test_dropThenLockStruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 42)
    def test_dropThenLockStruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 42)
    def test_dropThenLockStruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 42)
    def test_dropThenLockStruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 42)

    def test_lockWhileLiveStruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 7)
    def test_lockWhileLiveStruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 7)
    def test_lockWhileLiveStruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 7)
    def test_lockWhileLiveStruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 7)

    def test_weakFromLocalCRefStruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 7)
    def test_weakFromLocalCRefStruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 7)
    def test_weakFromLocalCRefStruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 7)
    def test_weakFromLocalCRefStruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 7)

    def test_weakFromCRefStruct_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 7)
    def test_weakFromCRefStruct_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 7)
    def test_weakFromCRefStruct_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 7)
    def test_weakFromCRefStruct_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefStruct.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 7)

    def test_dropThenLockInterface_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 42)
    def test_dropThenLockInterface_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 42)
    def test_dropThenLockInterface_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 42)
    def test_dropThenLockInterface_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/dropThenLockInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 42)

    def test_lockWhileLiveInterface_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 7)
    def test_lockWhileLiveInterface_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 7)
    def test_lockWhileLiveInterface_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 7)
    def test_lockWhileLiveInterface_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/lockWhileLiveInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 7)

    def test_weakFromLocalCRefInterface_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 7)
    def test_weakFromLocalCRefInterface_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 7)
    def test_weakFromLocalCRefInterface_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 7)
    def test_weakFromLocalCRefInterface_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromLocalCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 7)

    def test_weakFromCRefInterface_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 7)
    def test_weakFromCRefInterface_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 7)
    def test_weakFromCRefInterface_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 7)
    def test_weakFromCRefInterface_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/weakFromCRefInterface.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 7)

    def test_callWeakSelfMethodWhileLive_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodWhileLive.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 42)
    def test_callWeakSelfMethodWhileLive_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodWhileLive.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 42)
    def test_callWeakSelfMethodWhileLive_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodWhileLive.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 42)
    def test_callWeakSelfMethodWhileLive_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodWhileLive.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 42)

    def test_callWeakSelfMethodAfterDrop_assist(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodAfterDrop.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "assist", 0)
    def test_callWeakSelfMethodAfterDrop_unsafefast(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodAfterDrop.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "unsafe-fast", 0)
    def test_callWeakSelfMethodAfterDrop_resilientv0(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodAfterDrop.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v0", 0)
    def test_callWeakSelfMethodAfterDrop_resilientv1(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "weaks/callWeakSelfMethodAfterDrop.vale", PATH_TO_SAMPLES + "genericvirtuals/opt.vale"], "resilient-v1", 0)


if __name__ == '__main__':
    unittest.main()
