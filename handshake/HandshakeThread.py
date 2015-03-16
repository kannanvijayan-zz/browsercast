
import os
import sys
import subprocess
import tempfile
import BaseHTTPServer
import threading
import logging
import httplib
from Cheetah.Template import Template
import urlparse

LOG = logging.getLogger()
SERVE_PREFIX = 'html'

#BROWSER_PATH = '/usr/bin/firefox'
BROWSER_PATH = '/usr/bin/chromium'

class RequestMatcher:
    @staticmethod
    def CalculateMethodName(path_prefix):
        method_name = path_prefix.replace('/', '_')
        if method_name.startswith('_'):
            method_name = method_name[1:]
        return 'do_%s' % method_name

    def __init__(self, path_prefix, command=None, method_name=None):
        self.pathPrefix = path_prefix
        self.prefixLen = len(path_prefix)
        self.command = command
        if method_name is None:
            method_name = self.CalculateMethodName(path_prefix)
        self.methodName = method_name

    def attemptMatch(self, parsed_path, command):
        if not parsed_path.path.startswith(self.pathPrefix):
            return None

        if not (command is None):
            if command != self.command:
                return None

        # remove prefix from path
        relpath = parsed_path.path[self.prefixLen:]

        # parse query string
        if len(parsed_path.query) > 0:
            params = urlparse.parse_qs(parsed_path.query)
        else:
            params = {}

        return (relpath, params)

    def dispatchOn(self, handler, match_result):
        return getattr(handler, self.methodName)(*match_result)

REQUEST_METHODS = [
    RequestMatcher('/browsercast/register', 'GET')
]

class HandshakeRequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):
    def do_GET(self):
        self.dumpRequest()
        self.dispatchRequest()

    def do_POST(self):
        self.dumpRequest()
        self.dispatchRequest()

    def do_HEAD(self):
        self.dumpRequest()
        self.dispatchRequest()

    def dumpRequest(self):
        logging.info('Got HTTP %s from %s' % (self.command, self.client_address))
        logging.info('    Path=%s' % (self.path,))
        logging.info('    Version=%s' % (self.request_version,))
        logging.info('    Headers=%s' % (self.headers,))

    def dispatchRequest(self):
        parsed_path = urlparse.urlparse(self.path)
        command = self.command

        match = None
        for method in REQUEST_METHODS:
            match = method.attemptMatch(parsed_path, command)
            if match:
                method.dispatchOn(self, match)
                break

        # If no dispatch thingies match, send dummy response back.
        if not match:
            self.sendDebugResponse()

    def do_browsercast_register(self, subpath, params):
        # ensure that params indicate server addr
        if 'addr' not in params:
            self.sendBrowsercastError(httplib.BAD_REQUEST, "Caster address not sent.")
            return

        # check if another caster device is using the endpoint
        if self.server.isEndpointOccupied():
            self.sendBrowsercastError(httplib.FORBIDDEN, "The endpoint is already in use.")
            return

        # otherwise, try to start the browser.
        error_msg = self.server.startEndpointBrowser(params)
        if error_msg:
            self.sendBrowsercastError(httplib.INTERNAL_SERVER_ERROR,
                                      'Failed to start browser: %s' % (error_msg,))
            return

        # browser started successfully
        self.sendHTMLResponseHeader(httplib.OK)
        bindings = [self.getRequestBindings()]
        tmpl = self.server.getTemplateCached('browsercast_register_ok.tmpl')
        text = tmpl(searchList=bindings)
        self.wfile.write(text)
        return

    def sendBrowsercastError(self, code, error_message):
            self.sendHTMLResponseHeader(code)
            bindings = [{'error_message':error_message}, self.getRequestBindings()]
            tmpl = self.server.getTemplateCached('browsercast_register_fail.tmpl')
            text = tmpl(searchList=bindings)
            self.wfile.write(text)
            return

    def sendDebugResponse(self):
        self.sendHTMLResponseHeader()
        req_bindings = self.getRequestBindings()
        text = self.server.getTemplateCached('dump.tmpl')(searchList=[req_bindings])
        self.wfile.write(text)

    def sendHTMLResponseHeader(self, code=httplib.OK):
        self.send_response(code)
        self.send_header('Content-Type', 'text/html')
        self.end_headers()

    def getRequestBindings(self):
        return {
            'command': str(self.command),
            'client_address': str(self.client_address),
            'path': str(self.path),
            'request_version': str(self.request_version),
            'headers': str(self.headers)
        }

class HandshakeServer(BaseHTTPServer.HTTPServer):
    def __init__(self, server_address):
        BaseHTTPServer.HTTPServer.__init__(self, server_address, HandshakeRequestHandler)
        self.timeout = 0.25
        self.templateCache = {}

        # open '/dev/null' for input and leave it for later use.
        self.devNull = os.open('/dev/null', os.O_RDONLY)

        # info about currently running browser process
        self.browserProcess = None
        self.browserOutFile = None
        self.browserErrFile = None

    def getTemplateCached(self, filename):
        if not filename in self.templateCache:
            src = file(SERVE_PREFIX + '/' + filename).read()
            self.templateCache[filename] = Template.compile(src)
        return self.templateCache[filename]

    def handle_timeout(self):
        pass

    def isEndpointOccupied(self):
        return not (self.browserProcess is None)

    def startEndpointBrowser(self, params):
        if self.browserProcess:
            return "Browser already running."

        # open tempfiles or stderr and stdout.
        tout = self.makeTempfile('out')
        if tout is False:
            return "Failed to open tempfile."

        terr = self.makeTempfile('err')
        if terr is False:
            return "Failed to open tempfile."

        (temp_out, temp_out_name) = tout
        (temp_err, temp_err_name) = terr

        env = os.environ
        env['DISPLAY'] = ':0'

        url = self.makeTargetUrl(params)

        try:
            proc = subprocess.Popen([BROWSER_PATH, url],
                                    shell=False, close_fds=True, cwd='/tmp', env=env,
                                    stdin=self.devNull, stdout=temp_out, stderr=temp_err)
        except:
            exc = sys.exc_info()[1]
            return str(exc)

        self.browserProcess = proc
        self.browserOutFile = temp_out_name
        self.browserErrFile = temp_err_name

    def makeTargetUrl(self, params):
        parts = ['http://']
        parts.append(params['addr'][0])
        if ('port' in params) and (len(params['port']) > 0):
            parts.append(':')
            parts.append(str(params['port'][0]))
        if ('subpath' in params) and (len(params['subpath']) > 0):
            parts.append('/')
            parts.append(str(params['subpath'][0]))
        return ''.join(parts)

    def makeTempfile(self, tag):
        prefix = 'browsercast.%s.' % tag
        max_tries = 5
        tries = 0
        while True:
            path = tempfile.mktemp(prefix=prefix)
            try:
                fd = os.open(path, os.O_CREAT|os.O_EXCL|os.O_WRONLY)
                return (fd, path)
            except:
                tries += 1

            if tries >= max_tries:
                break

        return False

class HandshakeThread(threading.Thread):
    def __init__(self, port):
        super(HandshakeThread, self).__init__()

        self.stopLock = threading.Lock()
        self.stopRequested = False
        self.servePort = port

    def requestStop(self):
        logging.info("Requesting that HandshakeThread be stopped.")
        self.stopLock.acquire()
        self.stopRequested = True
        self.stopLock.release()

    def wasStopRequested(self):
        self.stopLock.acquire()
        stopReq = self.stopRequested
        self.stopLock.release()
        return stopReq

    def run(self):
        logging.info("HandshakeThread started.")

        http_server = HandshakeServer(('', self.servePort))

        while True:
            http_server.handle_request()
            if self.wasStopRequested():
                logging.info("HandshakeThread got stop request.  Stopping.")
                break
