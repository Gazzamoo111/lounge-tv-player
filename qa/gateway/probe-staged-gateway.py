"""Read-only smoke checks. Supply the designated QA line through environment variables.

Never prints or saves credentials, playlists, provider redirect URLs, or response bodies.
This script does not deploy a function, import streams, or change a catalogue.
"""
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


BASE = os.environ['LOUNGE_QA_GATEWAY'].rstrip('/')
USER = os.environ['LOUNGE_QA_USERNAME']
PASSWORD = os.environ['LOUNGE_QA_PASSWORD']
HEADERS = {'User-Agent': 'LoungeTV/Android/5.2.3.4', 'Accept': '*/*'}
OPENER = urllib.request.build_opener(NoRedirect)


def request(path, query=None, follow=False, limit=4_000_000):
    url = BASE + path
    if query:
        url += '?' + urllib.parse.urlencode(query)
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        response = (urllib.request.urlopen(req, timeout=30) if follow else OPENER.open(req, timeout=30))
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read(limit)


def api(action=None, username=USER, password=PASSWORD):
    query = {'username': username, 'password': password}
    if action:
        query['action'] = action
    return request('/player_api.php', query)


def decode_probe(url, user_agent=None):
    if urllib.parse.urlparse(url).scheme != 'https' and (USER in url or PASSWORD in url):
        return {'decoded_headers': False, 'reason': 'credentialed_http_requires_explicit_approval'}
    args = ['ffprobe', '-v', 'error', '-rw_timeout', '10000000']
    if user_agent:
        args += ['-user_agent', user_agent]
    args += ['-analyzeduration', '3000000', '-probesize', '3000000',
             '-show_entries', 'stream=codec_type,codec_name,width,height', '-of', 'json', url]
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=25)
        streams = json.loads(result.stdout or '{}').get('streams', [])
        return {'decoded_headers': result.returncode == 0 and bool(streams), 'streams': streams}
    except subprocess.TimeoutExpired:
        return {'decoded_headers': False, 'reason': 'timeout'}
    except Exception as error:
        return {'decoded_headers': False, 'reason': type(error).__name__}


def main():
    evidence = {'gateway': BASE, 'production_changed': False, 'checks': []}
    def record(name, **values):
        item = {'check': name, **values}
        evidence['checks'].append(item)
        print(json.dumps(item, ensure_ascii=False), flush=True)

    status, headers, body = api()
    data = json.loads(body)
    record('designated_account', http=status, authenticated=str(data.get('user_info', {}).get('auth')) == '1',
           qa_marker=headers.get('X-Lounge-QA'))
    status, _, body = api('get_live_streams')
    streams = json.loads(body)
    record('xtream_live_catalogue', http=status, rows=len(streams),
           valid_schema=isinstance(streams, list) and all('stream_id' in row and 'name' in row for row in streams))
    status, _, body = request('/get.php', {'username': USER, 'password': PASSWORD})
    playlist = body.decode('utf-8', errors='replace')
    record('m3u_output', http=status, header=playlist.startswith('#EXTM3U'), channels=playlist.count('#EXTINF:'),
           clear_nz_urls=all(url in playlist for url in (
               'https://i.mjh.nz/.r/three.m3u8', 'https://i.mjh.nz/.r/maori-tv.m3u8', 'https://i.mjh.nz/.r/te-reo.m3u8')),
           widevine_metadata=playlist.count('license_type=com.widevine.alpha'),
           qa_control_urls='/functions/v1/lounge-core-xtream-qa-next/live/' in playlist)
    prefix = '/live/' + urllib.parse.quote(USER, safe='') + '/' + urllib.parse.quote(PASSWORD, safe='') + '/'
    for name, item_id in [('Three', 1486145), ('Whakaata Māori', 1486490), ('Te Reo', 732),
                          ('TVNZ 1', 1486561), ('TVNZ 2', 1486578), ('BBC ONE', 811)]:
        status, headers, body = request(prefix + str(item_id) + '.ts')
        location = headers.get('Location', '')
        drm = item_id in (1486561, 1486578)
        record(name + '_route', http=status, drm_required=drm and b'drm_client_required' in body,
               nz_route=location.startswith('https://i.mjh.nz/.r/'),
               upstream_host=urllib.parse.urlparse(location).hostname,
               credential_free_nz=USER not in location and PASSWORD not in location if item_id != 811 else None)
        if status == 302 and not drm:
            result = decode_probe(location, 'otg/1.5.1 (AppleTv Apple TV 4; tvOS16.0; appletv.client) libcurl/7.58.0 OpenSSL/1.0.2o zlib/1.8.56' if item_id != 811 else None)
            record(name + '_media_probe', **result)
    status, _, _ = request('/live/invalid-qa-user/invalid-qa-password/1486145.ts')
    record('invalid_account_clear_nz', http=status, denied=status == 403)
    for name, stream_id in [('tennis', 1881735), ('tennis_placeholder', 1881712), ('unrelated', 2024978)]:
        path = '/sport/' + urllib.parse.quote(USER, safe='') + '/' + urllib.parse.quote(PASSWORD, safe='') + '/' + str(stream_id) + '.ts'
        status, headers, _ = request(path)
        record(name + '_sport_gate', http=status, redirect=status == 302,
               provider_id_retained=headers.get('Location', '').endswith('/' + str(stream_id) + '.ts'))
    output = os.environ.get('LOUNGE_QA_REPORT')
    if output:
        Path(output).write_text(json.dumps(evidence, indent=2, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main()
