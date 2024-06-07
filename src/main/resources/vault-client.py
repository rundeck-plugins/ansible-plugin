#!/usr/bin/env python3
import sys
import os
import getpass
import logging
from logging import handlers

log = logging.getLogger('')
log.setLevel(logging.DEBUG)

log_path=os.getenv('LOG_PATH', None)

if log_path:
    fh = handlers.RotatingFileHandler(log_path)
    log.addHandler(fh)
    log.info("Enter Password:")

if sys.stdin.isatty():
    secret = getpass.getpass()
else:
    secret = sys.stdin.readline().rstrip()

if secret is None:
    sys.stderr.write('ERROR: secret is not set\n')
    sys.exit(1)

sys.stdout.write('%s\n' % (secret))
sys.exit(0)
