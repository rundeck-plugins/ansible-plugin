#!/usr/bin/env python3
import sys
import os
import argparse
import getpass

parser = argparse.ArgumentParser(description='Get a vault password from user keyring')

parser.add_argument('--vault-id', action='store', default='',
                    dest='vault_id',
                    help='name of the vault secret to get from keyring')

args = parser.parse_args()
keyname = args.vault_id

secret=os.getenv('VAULT_ID_SECRET', None)

if secret:
    if keyname:
        sys.stdout.write('%s/%s\n' % (keyname,secret))
    else:
        sys.stdout.write('%s\n' % (secret))
    sys.exit(0)

if sys.stdin.isatty():
    secret = getpass.getpass()
else:
    secret = sys.stdin.readline().rstrip()

if secret is None:
    sys.stderr.write('ERROR: secret is not set\n')
    sys.exit(1)

if keyname:
    sys.stdout.write('%s/%s\n' % (keyname,secret))
else:
    sys.stdout.write('%s\n' % (secret))

sys.exit(0)
