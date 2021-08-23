#!python3
import argparse
import os
import os.path
import logging

from glob import glob


def main():
    parser = argparse.ArgumentParser(
        description="Github Workflow Preprocessor. This script will explode "
        "all aliases to anchors, then prune the root `anchors` entry. "
        "Requires yq to be on the PATH",
        epilog="Example: cd into .github and do: `python gwp.py ci.yml`"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="do not create workflow files"
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="verbosity on"
    )
    parser.add_argument(
        "WORKFLOW_PATH",
        nargs="*",
        default=["*.yml"],
        help="path to source workflow yaml file"
    )
    args = parser.parse_args()

    if args.verbose:
        logging.basicConfig(level=logging.DEBUG)

    logging.info(args)

    github_dir = os.path.dirname(os.path.realpath(__file__))

    for workflow_file in args.WORKFLOW_PATH:
        workflow_files = glob(workflow_file)
        outfiles = [f"_{workflow_file}" for workflow_file in workflow_files]
        outfiles = [
            ".".join([os.path.splitext(workflow_file)[0], "yml"])
            for workflow_file in outfiles
        ]

        logging.info(f"workflow files: {workflow_files}")
        logging.info(f"outfiles: {outfiles}")

        if not args.dry_run:
            for workflow_file, outfile in zip(workflow_files, outfiles):
                os.system(
                    r'yq e "'
                    r"explode(.) | "
                    r"del(.anchors) | "
                    r". headComment="
                    r'\"Do_not_edit_directly.'
                    r'_See_readme_section_editing_github_workflows\"'
                    r'"'
                    f" {workflow_file} > {github_dir}\\workflows\\{outfile}")


if __name__ == '__main__':
    main()
