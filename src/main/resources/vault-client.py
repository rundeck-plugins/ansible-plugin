#!/usr/bin/env python3
import sys
import os
import getpass
import logging
from logging import handlers
import argparse

log = logging.getLogger('')
log.setLevel(logging.DEBUG)
log_path=os.getenv('LOG_PATH', None)

parser = argparse.ArgumentParser(description='Get a vault password from user keyring')
parser.add_argument('--vault-id', action='store', default="None",
                    dest='vault_id',
                    help='name of the vault secret to get from keyring')

args = parser.parse_args()
vault_id = args.vault_id

if log_path:
    fh = handlers.RotatingFileHandler(log_path)
    log.addHandler(fh)
    log.info("Enter Password ("+vault_id+"):")

secret=os.getenv('VAULT_ID_SECRET', None)

if secret:
    sys.stdout.write('%s\n' % (secret))
    sys.exit(0)

if sys.stdin.isatty():
    secret = getpass.getpass()
else:
    secret = sys.stdin.readline().rstrip()

if secret is None:
    sys.stderr.write('ERROR: secret is not set\n')
    sys.exit(1)

sys.stdout.write('%s\n' % (secret))
sys.exit(0)
