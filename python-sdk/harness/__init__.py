"""The Harness Python SDK from ActionML
Provides easy-to-use functions for integrating
Python applications with ActionML's REST API for the Harness Server.
"""

__version__ = "0.2.0a2"

# import packages
import re
import datetime


try:
    import httplib
except ImportError:
    # pylint: disable=F0401
    # http is a Python3 module, replacing httplib
    from http import client as httplib

try:
    from urllib import quote
except ImportError:
    # pylint: disable=F0401,E0611
    from urllib.parse import quote

try:
    from urllib import urlencode
except ImportError:
    # pylint: disable=F0401,E0611
    from urllib.parse import urlencode

from datetime import datetime
import pytz

from harness.connection import Connection
from harness.connection import AsyncRequest
from harness.connection import AsyncResponse
from harness.connection import HarnessAPIError


class HttpError(HarnessAPIError):
    def __init__(self, message, response):
        super(HttpError, self).__init__(message)
        self.response = response


class UnexpectedStatusError(HttpError):
    def __init__(self, response):
        super(UnexpectedStatusError, self).__init__("Unexpected status: {}".format(response.status), response)


class NotImplementedError(HttpError):
    def __init__(self, response):
        super(NotImplementedError, self).__init__("Not Implemented: {}".format(response.request), response)


class BadRequestError(HttpError):
    def __init__(self, response):
        super(BadRequestError, self).__init__("Bad request: {}".format(response.request), response)


class NotFoundError(HttpError):
    def __init__(self, response):
        super(NotFoundError, self).__init__("Not found: {}".format(response.request), response)


def time_to_string_if_valid(t):
    """ Validate event_time according to EventAPI Specification."""

    if t is None:
        return datetime.now(pytz.utc).isoformat()

    if type(t) != datetime:
        raise AttributeError("event_time must be datetime.datetime")

    if t.tzinfo is None:
        raise AttributeError("event_time must have tzinfo")

    # EventServer uses milliseconds, but python datetime class uses micro. Hence
    # need to skip the last three digits.
    # pat: from PIO return t.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + t.strftime("%z")
    return t.isoformat()


class BaseClient(object):
    def __init__(self, url, threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        """Constructor of Client object."""
        self.threads = threads
        self.url = url
        self.qsize = qsize
        self.timeout = timeout

        # check connection type
        https_pattern = r'^https://(.*)'
        http_pattern = r'^http://(.*)'
        m = re.match(https_pattern, url)
        self.https = True
        if m is None:  # not matching https
            m = re.match(http_pattern, url)
            self.https = False
            if m is None:  # not matching http either
                raise InvalidArgumentError("url is not valid: %s" % url)
        self.host = m.group(1)

        self._uid = None  # identified uid
        self._connection = Connection(
            host=self.host,
            threads=self.threads,
            qsize=self.qsize,
            https=self.https,
            timeout=self.timeout,
            user_id=user_id,
            user_secret=user_secret
        )

    def close(self):
        """
        Close this client and the connection.
        Call this method when you want to completely terminate the connection with Harness.
        It will wait for all pending requests to finish.
        """
        self._connection.close()

    def pending_requests(self):
        """
        Return the number of pending requests.
        :returns: The number of pending requests of this client.
        """
        return self._connection.pending_requests()

    def get_status(self):
        """
        Get the status of the Harness API Server
        :returns: status message.
        :raises: ServerStatusError.
        """
        path = "/"
        request = AsyncRequest("GET", path)
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        result = request.get_response()
        return result

    def _add_segment(self, segment=None):
        if segment is not None:
            return "%s/%s" % (self.path, quote(segment, ""))
        else:
            return self.path

    def _add_get_params(self, path=None, **params):
        _path = self.path if path is None else path
        return "%s?%s" % (_path, urlencode(params))

    def _response_handler(self, expected_status, response):
        if response.error is not None:
            raise HttpError("Exception happened: {}".format(response.error), response)
        elif response.status == httplib.NOT_IMPLEMENTED:
            raise NotImplementedError(response)
        elif response.status == httplib.BAD_REQUEST:
            raise BadRequestError(response)
        elif response.status == httplib.NOT_FOUND:
            raise NotFoundError(response)
        elif response.status != expected_status:
            raise UnexpectedStatusError(response)
        return response

    def _create_response_handler(self, response):
        return self._response_handler(httplib.CREATED, response)

    def _ok_response_handler(self, response):
        return self._response_handler(httplib.OK, response)


class EventsClient(BaseClient):
    """
    Client for importing data into ActionML Harness Server.
    :param url: the url of ActionML Harness Server.
    :param threads: number of threads to handle ActionML API requests.
          Must be >= 1.
    :param qsize: the max size of the request queue (optional).
      The asynchronous request becomes blocking once this size has been
      reached, until the queued requests are handled.
      Default value is 0, which means infinite queue size.
    :param timeout: timeout for HTTP connection attempts and requests in
    seconds (optional).
    Default value is 5.
    """

    def __init__(self, engine_id, url="http://localhost:9090", threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        assert type(engine_id) is str, "engine_id must be string."
        self.engine_id = engine_id
        self.path = "/engines/%s/events" % (self.engine_id,)
        super(EventsClient, self).__init__(url, threads, qsize, timeout, user_id, user_secret)

    def async_create(self, event, entity_type, entity_id,
                     target_entity_type=None, target_entity_id=None, properties=None,
                     event_id=None, event_time=None, creation_time=None):
        """
        Asynchronously create an event.
        :param event_id:
        :param event: event name. type str.
        :param entity_type: entity type. It is the namespace of the entityId and
          analogous to the table name of a relational database. The entityId must be
          unique within same entityType. type str.
        :param entity_id: entity id. *entity_type-entity_id* becomes the unique
          identifier of the entity. For example, you may have entityType named user,
          and different entity IDs, say 1 and 2. In this case, user-1 and user-2
          uniquely identifies entities. type str
        :param target_entity_type: target entity type. type str.
        :param target_entity_id: target entity id. type str.
        :param properties: a custom dict associated with an event. type dict.
        :param event_time: the time of the event. type datetime, must contain
          timezone info.
        :param creation_time:
        :returns:
          AsyncRequest object. You can call the get_response() method using this
          object to get the final results or status of this asynchronous request.
        """
        data = {
            "eventId": event_id,
            "event": event,
            "entityType": entity_type,
            "entityId": entity_id,
        }

        if target_entity_type is not None:
            data["targetEntityType"] = target_entity_type

        if target_entity_id is not None:
            data["targetEntityId"] = target_entity_id

        if properties is not None:
            data["properties"] = properties

        data["eventTime"] = time_to_string_if_valid(event_time)

        data["creationTime"] = time_to_string_if_valid(creation_time)

        request = AsyncRequest("POST", self.path, **data)
        request.set_response_handler(self._create_response_handler)
        self._connection.make_request(request)
        return request

    def create(self, event, entity_type, entity_id,
               target_entity_type=None, target_entity_id=None, properties=None,
               event_id=None, event_time=None, creation_time=None):
        """Synchronously (blocking) create an event."""
        return self.async_create(event, entity_type, entity_id,
                                 target_entity_type, target_entity_id, properties,
                                 event_id, event_time, creation_time).get_response()

    def async_get(self, event_id):
        """
        Asynchronously get an event from Harness Server.
        :param event_id: event id returned by the EventServer when creating the event.
        :returns: AsyncRequest object.
        """
        request = AsyncRequest("GET", self._add_segment(event_id))
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def get(self, event_id):
        """Synchronouly get an event from Harness Server."""
        return self.async_get(event_id).get_response()

    def async_delete(self, event_id):
        """Asynchronouly delete an event from Harness Server.
        :param event_id: event id returned by the EventServer when creating the
          event.
        :returns:
          AsyncRequest object.
        """
        request = AsyncRequest("DELETE", self._add_segment(event_id))
        request.set_response_handler(self._delete_response_handler)
        self._connection.make_request(request)
        return request

    def delete(self, event_id):
        """Synchronously delete an event from Harness Server."""
        return self.async_delete(event_id).get_response()


class EnginesClient(BaseClient):
    def __init__(self, url="http://localhost:9090", threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        self.path = "/engines"
        super(EnginesClient, self).__init__(url, threads, qsize, timeout, user_id, user_secret)

    def async_get(self, engine_id):
        """
        Asynchronously get an engine info from Harness Server.
        :param engine_id:
        :returns: AsyncRequest object.
        """
        if engine_id is None:
            request = AsyncRequest("GET", self.path)
        else:
            request = AsyncRequest("GET", self._add_segment(engine_id))
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def get(self, engine_id):
        return self.async_get(engine_id).get_response()

    def async_create(self, data):
        """
        Asynchronously create engine.
        :param data:
        :return: 
        """

        request = AsyncRequest("POST", self.path, **data)
        request.set_response_handler(self._create_response_handler)
        self._connection.make_request(request)
        return request

    def create(self, data):
        return self.async_create(data).get_response()

    def async_update(self, engine_id, import_path, update_config, data):
        """
        Asynchronously update engine with either input events OR new config JSON
        :param engine_id: should be same as in data, which is json config string
        :param import_path: if non-empty, defines a path to input json files to import
        :param update_config: if True means the data = JSON config params for engine
        :param data: json config data, as in create, engine_id's passed in and in json MUST match
        :return:
        """
        path = self._add_segment(engine_id)

        if update_config:
            path = path + "/configs"  # endpoint on an engine-id, flagging that data is the new config JSON
        else:
            path = path + "/imports"  # endpoint on an engine-id, that tells the engine to import from the import_path
            query = {'import_path': import_path}
            path = self._add_get_params(path, **query)

        # print("Fully constructed path: {}".format(path))
        # print("Data supplied: {}".format(data))

        request = AsyncRequest("POST", path, **data)

    #        if :
    #            query['force'] = True
    #        if input is not None:
    #            query['input'] = input

    #        path = self._add_segment(engine_id)
    #        path = self._add_get_params(path, **query)

    #        request = AsyncRequest("POST", path, **data)
    #        request.set_response_handler(self._ok_response_handler)
    #        self._connection.make_request(request)
    #        return request

        # path = self._add_get_params(path, **query)

        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def update(self, engine_id, import_path, update_config, data):
        req = self.async_update(engine_id, import_path, update_config, data)
        # print("Made request: {}".format(req))
        ret = req.get_response()
        # print("Got response: {}".format(ret))
        return ret

    def async_delete(self, engine_id):
        request = AsyncRequest("DELETE", self._add_segment(engine_id))
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def delete(self, engine_id):
        return self.async_delete(engine_id).get_response()


class QueriesClient(BaseClient):
    """
    Client for extracting prediction results from an Harness Engine
    Instance.
    :param url: the url of the Harness Engine Instance.
    :param threads: number of threads to handle Harness API requests. Must be >= 1.
    :param qsize: the max size of the request queue (optional).
        The asynchronous request becomes blocking once this size has been
        reached, until the queued requests are handled.
        Default value is 0, which means infinite queue size.
    :param timeout: timeout for HTTP connection attempts and requests in seconds (optional). Default value is 5.
    """

    def __init__(self, engine_id, url = "http://localhost:9090", threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        self.engine_id = engine_id
        self.path = "/engines/{}/queries".format(self.engine_id)
        super(QueriesClient, self).__init__(url, threads, qsize, timeout, user_id, user_secret)

    def async_send_query(self, data):
        """
        Asynchronously send a request to the engine instance with data as the query.
        :param data: the query: It is converted to an json object using json.dumps method. type dict.
        :returns: 
            AsyncRequest object. You can call the get_response() method using this
            object to get the final results or status of this asynchronous request.
        """

        request = AsyncRequest("POST", self.path, **data)
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def send_query(self, data):
        """
        Synchronously send a request.
        :param data: the query: It is converted to an json object using json.dumps method. type dict.
        :returns: the prediction.
        """
        return self.async_send_query(data).get_response()


class CommandsClient(BaseClient):
    def __init__(self, url = "http://localhost:9090", threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        self.path = "/commands"
        super(CommandsClient, self).__init__(url, threads, qsize, timeout, user_id, user_secret)

    def async_get_engines_list(self):
        request = AsyncRequest("GET", self.path + "/list/engines")
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def get_engines_list(self):
        return self.async_get_engines_list().get_response()

    def async_get_commands_list(self):
        request = AsyncRequest("GET", self.path + "/list/commands")
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def get_commands_list(self):
        return self.async_get_commands_list().get_response()

    def async_run_command(self, engine_id):
        data = {}
        if engine_id is not None:
            data['engine_id'] = engine_id

        request = AsyncRequest("POST", self.path + "/batch-train", **data)
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def run_command(self, engine_id):
        return self.async_run_command(engine_id).get_response()

    def async_check_command(self, command_id):
        request = AsyncRequest("GET", self._add_segment(command_id))
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def check_command(self, command_id):
        return self.async_check_command(command_id).get_response()

    def async_cancel_command(self, command_id):
        request = AsyncRequest("DELETE", self._add_segment(command_id))
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def cancel_command(self, command_id):
        return self.async_cancel_command(command_id).get_response()


class UsersClient(BaseClient):

    def __init__(self, url="http://localhost:9090", threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        self.path = "/auth/users"
        super().__init__(url, threads, qsize, timeout, user_id, user_secret)

    def async_get(self, queried_user_id):
        """
        Asynchronously get an user info from Harness Server.
        :param user_id:
        :returns: AsyncRequest object.
        """
        if queried_user_id is None:
            request = AsyncRequest("GET", self.path)
        else:
            request = AsyncRequest("GET", self._add_segment(queried_user_id))
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request

    def get(self, queried_user_id):
        return self.async_get(queried_user_id).get_response()

    def create_user(self, role_set_id=None, resource_id=None):
        request = AsyncRequest("POST", self.path, roleSetId=role_set_id, resourceId=resource_id)
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request.get_response()


class PermissionsClient(BaseClient):

    def __init__(self, url="http://localhost:9090", threads=1, qsize=0, timeout=5, user_id=None, user_secret=None):
        self.path = "/auth/users"
        super().__init__(url, threads, qsize, timeout, user_id, user_secret)

    def grant_permission(self, permitted_user_id, role_set_id, resource_id):
        path = self.path + "/{}/permissions".format(permitted_user_id)
        request = AsyncRequest("POST", path, roleSetId=role_set_id, resourceId=resource_id)
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request.get_response()

    def revoke_permission(self, permitted_user_id, role_set_id):
        path = self.path + "/{}/permissions".format(permitted_user_id)
        request = AsyncRequest("DELETE", path, roleSetId=role_set_id)
        request.set_response_handler(self._ok_response_handler)
        self._connection.make_request(request)
        return request.get_response()

