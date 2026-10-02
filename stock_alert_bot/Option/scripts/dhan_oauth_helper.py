from flask import Flask, request, render_template_string
import os
import webbrowser

app = Flask(__name__)

HTML = '''
<!doctype html>
<title>Dhan OAuth Helper</title>
<h3>Paste your Dhan access token</h3>
<form method="post">
  <textarea name="token" rows="4" cols="80"></textarea><br>
  <input type="submit" value="Save token">
</form>
'''


@app.route('/', methods=['GET', 'POST'])
def index():
    if request.method == 'POST':
        token = request.form.get('token')
        if token:
            os.makedirs('data', exist_ok=True)
            path = os.path.join('data', 'dhan_access_token.txt')
            with open(path, 'w') as f:
                f.write(token.strip())
            return f'Token saved to {path}. You can close this page.'
    return render_template_string(HTML)


def open_browser(port=5000):
    url = f'http://127.0.0.1:{port}/'
    try:
        webbrowser.open(url)
    except Exception:
        print(f'Open this URL in your browser: {url}')


if __name__ == '__main__':
    port = int(os.environ.get('DHAN_OAUTH_PORT', '5000'))
    print('Starting local OAuth helper. Visit / open the page to paste token.')
    open_browser(port)
    app.run(port=port)
