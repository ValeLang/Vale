import json
import argparse


def main():
    parser = argparse.ArgumentParser(description="prettify VIR")
    parser.add_argument("VIR_FILE", help="path to VIR file")
    args = parser.parse_args()

    with open(args.VIR_FILE, "r") as vir_f:
        j = json.load(vir_f)
    with open(args.VIR_FILE, "w") as vir_f:
        json.dump(j, fp=vir_f, indent=3)


if __name__ == '__main__':
    main()
