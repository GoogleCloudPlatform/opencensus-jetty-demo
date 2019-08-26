# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Generates test data to be stored in GCS and read by the Jetty client.
"""

import json

snumbers = []
for i in range(500):
  snumbers.append(i)
sdata = {}
sdata['numbers'] = snumbers
with open('small_file.json', 'w') as outfile:
  json.dump(sdata, outfile)

lnumbers = []
for i in range(100000):
  lnumbers.append(i)
ldata = {}
ldata['numbers'] = lnumbers
with open('large_file.json', 'w') as outfile:
  json.dump(ldata, outfile)
