#!/usr/bin/env python3
import sys
import os
import getpass

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
