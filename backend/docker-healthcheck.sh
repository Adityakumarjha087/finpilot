#!/bin/bash
# Readiness probe using bash's /dev/tcp (the JRE base image ships without curl/wget).
exec 3<>/dev/tcp/127.0.0.1/8080 || exit 1
printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
read -r -t 4 status_line <&3 || exit 1
case "$status_line" in
  *" 200 "*) exit 0 ;;
  *) exit 1 ;;
esac
