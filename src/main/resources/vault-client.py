#!/usr/bin/env python3

import sys
import os
import argparse

parser = argparse.ArgumentParser(description='Get a vault password from user keyring')

parser.add_argument('--vault-id', action='store', default='dev',
                        dest='vault_id',
                        help='name of the vault secret to get from keyring')

args = parser.parse_args()
keyname = args.vault_id
secret=os.environ["VAULT_ID_SECRET"]

if secret is None:
    sys.stderr.write('ERROR: VAULT_ID_SECRET is not set\n')
    sys.exit(1)

sys.stdout.write('%s/%s\n' % (keyname,secret))

