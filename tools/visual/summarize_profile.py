"""Summarize vanilla F3+L client metrics without interpreting software GL as GPU hardware performance."""
from pathlib import Path
import argparse
import csv
import io
import json
import statistics
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('archive', type=Path)
args = parser.parse_args()
with zipfile.ZipFile(args.archive) as archive:
    def rows(name):
        return list(csv.DictReader(io.StringIO(archive.read('client/metrics/'+name+'.csv').decode())))
    times=sorted(float(r['ticktime'])/1e6 for r in rows('ticking'))
    queue=rows('chunk_rendering_dispatching')
    print(json.dumps({'archive':str(args.archive),'frames':len(times),'mean_frame_ms':round(statistics.mean(times),2),
                      'p95_frame_ms':round(times[int(len(times)*.95)],2),
                      'max_pending_uploads':max(float(r['toUpload']) for r in queue),
                      'max_pending_batches':max(float(r['toBatchCount']) for r in queue)},indent=2))
