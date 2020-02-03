#!/bin/sh
echo "packaging ezjiv..." >&2
jar cvfe ezjiv.jar noTLD.bmdeeal.ezjiv.ezjiv -C ./class/ .
