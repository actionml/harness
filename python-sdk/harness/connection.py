try:
    import Queue
except ImportError:
    # pylint: disable=F0401
    # http is a Python3 module, replacing httplib. Ditto.
    import queue as Queue
import threading

try:
    import httplib
except ImportError:
    # pylint: disable=F0401
    from http import client as httplib

try:
    from urllib import urlencode
except ImportError:
    # pylint: disable=F0401,E0611
    from urllib.parse import urlencode

import datetime
import json
import logging
import base64
import os

try:
    import ssl
except ImportError:
    print("error: no ssl support")

# use generators for python2 and python3
try:
    xrange
except NameError:
    xrange = range

# some constants
MAX_RETRY = 1  # 0 means no retry

# logger
logger = None
DEBUG_LOG = False


def enable_log(filename=None):
    global logger
    global DEBUG_LOG
    timestamp = datetime.datetime.today()
    if not filename:
        logfile = "./log/harness_%s.log" % timestamp.strftime(
            "%Y-%m-%d_%H:%M:%S.%f")
    else:
        logfile = filename
    logging.basicConfig(filename=logfile,
                        filemode='w',
                        level=logging.DEBUG,
                        format='[%(levelname)s] %(name)s (%(threadName)s) %(message)s')
    logger = logging.getLogger(__name__)
    DEBUG_LOG = True


class HarnessAPIError(Exception):
    pass


class NotSupportMethodError(HarnessAPIError):
    pass


class ProgramError(HarnessAPIError):
    pass


class AsyncRequest(object):
    """AsyncRequest object
    """

    def __init__(self, method, path, **params):
        self.method = method  # "GET" "POST" etc
        # the sub path eg. POST /v1/users.json  GET /v1/users/1.json
        self.path = path
        # dictionary format eg. {"appkey" : 123, "id" : 3}
        self.params = params
        # use queue to implement response, store AsyncResponse object
        self.response_q = Queue.Queue(1)
        if method in ["GET", "HEAD", "DELETE"]:
            self.qpath = "%s?%s" % (self.path, urlencode(self.params))
        else:
            self.qpath = self.path
        self._response = None
        # response function to be called to handle the response
        self.response_handler = None

    def __str__(self):
        return "%s %s %s %s" % (self.method, self.path, self.params,
                                self.qpath)

    def set_response_handler(self, handler):
        self.response_handler = handler

    def set_response(self, response):
        """ store the response
        NOTE: Must be only called once
        """
        self.response_q.put(response)

    def get_response(self):
        """
        Get the response. Blocking.
        :returns: self.response_handler's return type.
        """
        if self._response is None:
            tmp_response = self.response_q.get(True)  # NOTE: blocking
            if self.response_handler is None:
                self._response = tmp_response
            else:
                self._response = self.response_handler(tmp_response)

        return self._response


class AsyncResponse(object):
    """
    Store the response of asynchronous request
    When get the response, user should check if error is None (which means no Exception happens).
    If error is None, then should check if the status is expected.
    """

    def __init__(self):
        #: exception object if any happens
        self.error = None

        self.version = None
        self.status = None
        self.reason = None
        #: Response header. str
        self.headers = None
        #: Response body. str
        self.body = None
        #: Jsonified response body. Remains None if conversion is unsuccessful.
        self.json_body = None
        #: Point back to the AsyncRequest object
        self.request = None

    def __str__(self):
        return "e:%s v:%s s:%s r:%s h:%s b:%s" % (self.error, self.version,
                                                  self.status, self.reason,
                                                  self.headers, self.body)

    def set_resp(self, version, status, reason, headers, body):
        self.version = version
        self.status = status
        self.reason = reason
        self.headers = headers
        self.body = body
        # Try to extract the json.
        try:
            self.json_body = json.loads(body)
        except ValueError as ex:  # noqa
            self.json_body = str(body)

    def set_error(self, error):
        self.error = error


class AsyncResponse(object):
    """
    Store the response of asynchronous request
    When get the response, user should check if error is None (which means no Exception happens).
    If error is None, then should check if the status is expected.
    """

    def __init__(self):
        #: exception object if any happens
        self.error = None

        self.version = None
        self.status = None
        self.reason = None
        #: Response header. str
        self.headers = None
        #: Response body. str
        self.body = None
        #: Jsonified response body. Remains None if conversion is unsuccessful.
        self.json_body = None
        #: Point back to the AsyncRequest object
        self.request = None

    def __str__(self):
        return "e:%s v:%s s:%s r:%s h:%s b:%s" % (self.error, self.version,
                                                  self.status, self.reason,
                                                  self.headers, self.body)

    def set_resp(self, version, status, reason, headers, body):
        self.version = version
        self.status = status
        self.reason = reason
        self.headers = headers
        self.body = body
        # Try to extract the json.
        try:
            self.json_body = json.loads(body.decode('utf8'))
        except ValueError as ex:
            self.json_body = body.decode()

    def set_error(self, error):
        self.error = error

    def set_request(self, request):
        self.request = request


class HarnessHttpConnection(object):
    def __init__(self, host, https=False, timeout=5):
        self.access_token = None
        if https:  # https connection
            ca_file = os.getenv("HARNESS_SERVER_CERT_PATH", "harness.pem")
            ssl_context = ssl.create_default_context(cafile=ca_file)
            self._connection = httplib.HTTPSConnection(host, timeout=timeout, context=ssl_context)
        else:
            self._connection = httplib.HTTPConnection(host, timeout=timeout)

    def connect(self):
        self._connection.connect()

    def close(self):
        self._connection.close()

    def get_access_token_header(self, user_id, user_secret):
        body = 'grant_type=client_credentials'
        basic_string = '{username}:{password}'.format(username=user_id, password=user_secret)
        user_creds = base64.b64encode(basic_string.encode('utf-8')).decode('utf-8')
        headers = {"Authorization": ('Basic ' + user_creds), "Content-Type": "application/x-www-form-urlencoded"}
        self._connection.request("POST", "/auth/token", body, headers)
        resp = self._connection.getresponse()
        resp_body = resp.read().decode()
        token = json.loads(resp_body)["access_token"]
        return 'Bearer ' + token

    def with_auth_header(self, headers, user_id, user_secret):
        auth_enabled = (user_id is not None) & (user_secret is not None)
        if auth_enabled:
            if self.access_token is None:
                token = self.get_access_token_header(user_id, user_secret)
            else:
                token = self.access_token
            headers["Authorization"] = token
        return headers

    def request(self, method, url, body={}, headers={}, user_id=None, user_secret=None):
        """
        http request wrapper function, with retry capability in case of error.
        catch error exception and store it in AsyncResponse object
        return AsyncResponse object
        Args:
          method: http method, type str
          url: url path, type str
          body: http request body content, type dict
          header: http request header , type dict
        """

        response = AsyncResponse()

        try:
            # number of retry in case of error (minimum 0 means no retry)
            retry_limit = MAX_RETRY
            mod_headers = dict(headers)  # copy the headers
            mod_headers["Connection"] = "keep-alive"
            mod_headers = self.with_auth_header(mod_headers, user_id, user_secret)
            enc_body = None
            if body:  # if body is not empty
                # enc_body = urlencode(body)
                # mod_headers[
                # "Content-type"] = "application/x-www-form-urlencoded"
                enc_body = json.dumps(body)
                mod_headers["Content-type"] = "application/json"
                # mod_headers["Accept"] = "text/plain"
        except Exception as e:
            response.set_error(e)
            return response

        if DEBUG_LOG:
            logger.debug("Request m:%s u:%s h:%s b:%s", method, url,
                         mod_headers, enc_body)
        # retry loop
        for i in xrange(retry_limit + 1):
            try:
                if i != 0:
                    if DEBUG_LOG:
                        logger.debug("retry request %s times" % i)
                if self._connection.sock is None:
                    self._connection.connect()
                self._connection.request(method, url, enc_body, mod_headers)
            except Exception as e:
                self._connection.close()
                if i == retry_limit:
                    # new copy of e created everytime??
                    response.set_error(e)
            else:  # NOTE: this is try's else clause
                # connect() and request() OK
                try:
                    resp = self._connection.getresponse()
                except Exception as e:
                    self._connection.close()
                    if i == retry_limit:
                        response.set_error(e)
                else:  # NOTE: this is try's else clause
                    # getresponse() OK
                    resp_version = resp.version  # int
                    resp_status = resp.status  # int
                    resp_reason = resp.reason  # str
                    # resp.getheaders() returns list of tuples
                    # converted to dict format
                    resp_headers = dict(resp.getheaders())
                    # NOTE: have to read the response before sending out next
                    # http request
                    resp_body = resp.read()  # str
                    response.set_resp(version=resp_version, status=resp_status,
                                      reason=resp_reason, headers=resp_headers,
                                      body=resp_body)
                    break  # exit retry loop
        # end of retry loop
        if DEBUG_LOG:
            logger.debug("Response %s", response)
        return response  # AsyncResponse object


def connection_worker(host, request_queue, https=False, timeout=5, loop=True):
    """worker function which establishes connection and wait for request jobs
    from the request_queue
    Args:
      request_queue: the request queue storing the AsyncRequest object
        valid requests:
          GET
          POST
          DELETE
          KILL
      https: HTTPS (True) or HTTP (False)
      timeout: timeout for HTTP connection attempts and requests in seconds
      loop: This worker function stays in a loop waiting for request
        For testing purpose only. should always be set to True.
        :param loop:
        :param timeout: 
        :param request_queue: 
        :param https: 
        :param host:  
    """

    connect = HarnessHttpConnection(host, https, timeout)

    # loop waiting for job form request queue
    killed = not loop

    while True:
        # print "thread %s waiting for request" % thread.get_ident()
        request = request_queue.get(True)  # NOTE: blocking get
        # print "get request %s" % request
        method = request.method
        user_id = request.user_id
        user_secret = request.user_secret
        if method == "GET":
            path = request.qpath
            d = connect.request("GET", path, user_id=user_id, user_secret=user_secret)
        elif method == "POST":
            path = request.path
            body = request.params
            d = connect.request("POST", path, body, user_id=user_id, user_secret=user_secret)
        elif method == "DELETE":
            path = request.qpath
            d = connect.request("DELETE", path, user_id=user_id, user_secret=user_secret)
        elif method == "KILL":
            # tell the thread to kill the connection
            killed = True
            d = AsyncResponse()
        else:
            d = AsyncResponse()
            d.set_error(NotSupportMethodError(
                "Don't Support the method %s" % method))

        d.set_request(request)
        request.set_response(d)
        request_queue.task_done()
        if killed:
            break

    # end of while loop
    connect.close()


class Connection(object):
    """abstract object for connection with server
    spawn multiple connection_worker threads to handle jobs in the queue q
    """

    def __init__(self, host, threads=1, qsize=0, https=False, timeout=5, user_id=None, user_secret=None):
        """constructor
        Args:
          host: host of the server.
          threads: type int, number of threads to be spawn
          qsize: size of the queue q
          https: indicate it is httpS (True) or http connection (False)
          timeout: timeout for HTTP connection attempts and requests in
            seconds
        """
        self.host = host
        self.https = https
        self.q = Queue.Queue(qsize)  # if qsize=0, means infinite
        self.threads = threads
        self.timeout = timeout
        self.user_id = user_id
        self.user_secret = user_secret
        # start thread based on threads number
        self.tid = {}  # dictionary of thread object

        for i in xrange(threads):
            tname = "HarnessThread-%s" % i  # thread name
            self.tid[i] = threading.Thread(
                target=connection_worker, name=tname,
                kwargs={'host': self.host, 'request_queue': self.q,
                        'https': self.https, 'timeout': self.timeout})
            self.tid[i].setDaemon(True)
            self.tid[i].start()

    def make_request(self, request):
        """put the request into the q
        """
        request.user_id = self.user_id
        request.user_secret = self.user_secret
        self.q.put(request)

    def pending_requests(self):
        """number of pending requests in the queue
        """
        return self.q.qsize()

    def close(self):
        """close this Connection. Call this when main program exits
        """
        # set kill message to q
        for i in xrange(self.threads):
            self.make_request(AsyncRequest("KILL", ""))

        self.q.join()  # wait for q empty

        for i in xrange(self.threads):  # wait for all thread finish
            self.tid[i].join()
