"""ActionML Python SDK
The ActionML Python SDK provides easy-to-use functions for integrating
Python applications with ActionML REST API services.
"""

__version__ = "0.0.1"

# import packages
import re

try:
    import httplib
except ImportError:
    # pylint: disable=F0401
    # http is a Python3 module, replacing httplib
    from http import client as httplib

try:
    from urllib import urlencode
except ImportError:
    # pylint: disable=F0401,E0611
    from urllib.parse import urlencode

import json
import urllib

from datetime import datetime
import pytz

from actionml.connection import Connection
from actionml.connection import AsyncRequest
from actionml.connection import AsyncResponse
from actionml.connection import ActionMLAPIError


class NotCreatedError(ActionMLAPIError):
    pass


class NotFoundError(ActionMLAPIError):
    pass


def time_to_string_if_valid(t: datetime):
    """ Validate event_time according to EventAPI Specification."""

    if t is None:
        return datetime.now(pytz.utc)

    if type(t) != datetime:
        raise AttributeError("event_time must be datetime.datetime")

    if t.tzinfo is None:
        raise AttributeError("event_time must have tzinfo")

    # EventServer uses milliseconds, but python datetime class uses micro. Hence
    # need to skip the last three digits.
    return t.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + t.strftime("%z")


class BaseClient(object):
    def __init__(self, url, threads=1, qsize=0, timeout=5):
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
            timeout=self.timeout
        )

    def close(self):
        """
        Close this client and the connection.
        Call this method when you want to completely terminate the connection with ActionML.
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
        Get the status of the ActionML API Server
        :returns: status message.
        :raises: ServerStatusError.
        """
        path = "/"
        request = AsyncRequest("GET", path)
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        result = request.get_response()
        return result

    def _add_segment(self, segment: str):
        print(segment, self.path)
        return self.path + "/" + urllib.parse.quote(segment, "")

    def _create_response_handler(self, response):
        if response.error is not None:
            raise NotCreatedError("Exception happened: %s for request %s" %
                                  (response.error, response.request))
        elif response.status != httplib.CREATED:
            raise NotCreatedError("request: %s status: %s body: %s" %
                                  (response.request, response.status,
                                   response.body))

        return response

    def _get_response_handler(self, response):
        if response.error is not None:
            raise NotFoundError("Exception happened: %s for request %s" %
                                (response.error, response.request))
        elif response.status != httplib.OK:
            raise NotFoundError("request: %s status: %s body: %s" %
                                (response.request, response.status,
                                 response.body))

        return response.json_body

    def _delete_response_handler(self, response):
        if response.error is not None:
            raise NotFoundError("Exception happened: %s for request %s" %
                                (response.error, response.request))
        elif response.status != httplib.OK:
            raise NotFoundError("request: %s status: %s body: %s" %
                                (response.request, response.status,
                                 response.body))

        return response.body


class EventClient(BaseClient):
    """
    Client for importing data into ActionML PIO Kappa Server.
    :param url: the url of ActionML PIO Kappa Server.
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

    def __init__(self, dataset_id, url="http://localhost:8080", threads=1, qsize=0, timeout=5):
        assert type(dataset_id) is str, "dataset_id must be string."
        self.dataset_id = dataset_id
        self.path = "/datasets/%s/events" % (self.dataset_id,)
        super(EventClient, self).__init__(url, threads, qsize, timeout)

    def async_create(self, event_id, event, entity_type, entity_id,
                     target_entity_type=None, target_entity_id=None, properties=None,
                     event_time=None, creation_time=None):
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

    def create(self, event_id: str, event: str, entity_type: str, entity_id: str,
               target_entity_type: str = None, target_entity_id: str = None, properties: object = None,
               event_time: datetime = None, creation_time: datetime = None):
        """Synchronously (blocking) create an event."""
        return self.async_create(event_id, event, entity_type, entity_id,
                                 target_entity_type, target_entity_id, properties,
                                 event_time, creation_time).get_response()

    def async_get(self, event_id: str):
        """
        Asynchronously get an event from PIO Kappa Server.
        :param event_id: event id returned by the EventServer when creating the event.
        :returns: AsyncRequest object.
        """
        request = AsyncRequest("GET", self._add_segment(event_id))
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        return request

    def get(self, event_id):
        """Synchronouly get an event from PIO Kappa Server."""
        return self.async_get(event_id).get_response()

    def async_delete(self, event_id):
        """Asynchronouly delete an event from PIO Kappa Server.
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
        """Synchronously delete an event from PIO Kappa Server."""
        return self.async_delete(event_id).get_response()

    ## Below are helper functions

    def async_set_user(self, uid, properties=None, event_time=None):
        """
        Set properties of a user.
        Wrapper of async_create function, setting event to "$set" and entity_type
        to "user".
        """
        if properties is None:
            properties = {}
        return self.async_create(
            event="$set",
            entity_type="user",
            entity_id=uid,
            properties=properties,
            event_time=event_time,
        )

    def set_user(self, uid, properties=None, event_time=None):
        """Set properties of a user"""
        if properties is None:
            properties = {}
        return self.async_set_user(uid, properties, event_time).get_response()

    def async_unset_user(self, uid, properties, event_time=None):
        """
        Unset properties of an user.
        Wrapper of async_create function, setting event to "$unset" and entity_type
        to "user".
        """
        # check properties={}, it cannot be empty
        return self.async_create(
            event="$unset",
            entity_type="user",
            entity_id=uid,
            properties=properties,
            event_time=event_time,
        )

    def unset_user(self, uid, properties, event_time=None):
        """Set properties of a user"""
        return self.async_unset_user(uid, properties, event_time).get_response()

    def async_delete_user(self, uid, event_time=None):
        """
        Delete a user.
        Wrapper of async_create function, setting event to "$delete" and entity_type
        to "user".
        """
        return self.async_create(
            event="$delete",
            entity_type="user",
            entity_id=uid,
            event_time=event_time)

    def delete_user(self, uid, event_time=None):
        """Delete a user."""
        return self.async_delete_user(uid, event_time).get_response()

    def async_set_item(self, iid, properties=None, event_time=None):
        """
        Set properties of an item.
        Wrapper of async_create function, setting event to "$set" and entity_type
        to "item".
        """
        if properties is None:
            properties = {}
        return self.async_create(
            event="$set",
            entity_type="item",
            entity_id=iid,
            properties=properties,
            event_time=event_time)

    def set_item(self, iid, properties=None, event_time=None):
        """Set properties of an item."""
        if properties is None:
            properties = {}
        return self.async_set_item(iid, properties, event_time).get_response()

    def async_unset_item(self, iid, properties=None, event_time=None):
        """
        Unset properties of an item.
        Wrapper of async_create function, setting event to "$unset" and entity_type
        to "item".
        """
        if properties is None:
            properties = {}
        return self.async_create(
            event="$unset",
            entity_type="item",
            entity_id=iid,
            properties=properties,
            event_time=event_time)

    def unset_item(self, iid, properties=None, event_time=None):
        """Unset properties of an item."""
        if properties is None:
            properties = {}
        return self.async_unset_item(iid, properties, event_time).get_response()

    def async_delete_item(self, iid, event_time=None):
        """
        Delete an item.
        Wrapper of async_create function, setting event to "$delete" and entity_type
        to "item".
        """
        return self.async_create(
            event="$delete",
            entity_type="item",
            entity_id=iid,
            event_time=event_time)

    def delete_item(self, iid, event_time=None):
        """Delete an item."""
        return self.async_delete_item(iid, event_time).get_response()

    def async_record_user_action_on_item(self, action, uid, iid, properties=None, event_time=None):
        """
        Create a user-to-item action.
        Wrapper of async_create function, setting entity_type to "user" and
        target_entity_type to "item".
        """
        if properties is None:
            properties = {}
        return self.async_create(
            event=action,
            entity_type="user",
            entity_id=uid,
            target_entity_type="item",
            target_entity_id=iid,
            properties=properties,
            event_time=event_time)

    def record_user_action_on_item(self, action, uid, iid, properties=None, event_time=None):
        """Create a user-to-item action."""
        if properties is None:
            properties = {}
        return self.async_record_user_action_on_item(
            action, uid, iid, properties, event_time).get_response()


class DatasetClient(BaseClient):
    def __init__(self, url: str = "http://localhost:8080", threads=1, qsize=0, timeout=5):
        self.path = "/datasets"
        super(DatasetClient, self).__init__(url, threads, qsize, timeout)

    def async_get(self, dataset_id: str):
        """
        Asynchronously get an dataset info from PIO Kappa Server.
        :param dataset_id:
        :returns: AsyncRequest object.
        """
        request = AsyncRequest("GET", self._add_segment(dataset_id))
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        return request

    def get(self, dataset_id: str):
        return self.async_get(dataset_id).get_response()

    def async_create(self, dataset_id: str = None):
        """
        Asynchronously create dataset.
        :param dataset_id: 
        :return: 
        """
        data = {}
        if dataset_id is not None:
            data['id'] = dataset_id

        request = AsyncRequest("POST", self.path, **data)
        request.set_response_handler(self._create_response_handler)
        self._connection.make_request(request)
        return request

    def create(self, dataset_id: str = None):
        return self.async_create(dataset_id).get_response()

    def async_delete(self, dataset_id: str):
        request = AsyncRequest("DELETE", self._add_segment(dataset_id))
        request.set_response_handler(self._delete_response_handler)
        self._connection.make_request(request)
        return request

    def delete(self, dataset_id: str):
        return self.async_delete(dataset_id).get_response()


class EngineClient(BaseClient):
    def __init__(self, url: str = "http://localhost:8080", threads=1, qsize=0, timeout=5):
        self.path = "/engines"
        super(EngineClient, self).__init__(url, threads, qsize, timeout)

    def async_get(self, engine_id: str):
        """
        Asynchronously get an engine info from PIO Kappa Server.
        :param engine_id:
        :returns: AsyncRequest object.
        """
        request = AsyncRequest("GET", self._add_segment(engine_id))
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        return request

    def get(self, engine_id: str):
        return self.async_get(engine_id).get_response()

    def async_create(self, engine_id: str = None):
        """
        Asynchronously create engine.
        :param engine_id: 
        :return: 
        """
        data = {}
        if engine_id is not None:
            data['id'] = engine_id

        request = AsyncRequest("POST", self.path, **data)
        request.set_response_handler(self._create_response_handler)
        self._connection.make_request(request)
        return request

    def create(self, engine_id: str = None):
        return self.async_create(engine_id).get_response()

    def async_delete(self, engine_id: str):
        request = AsyncRequest("DELETE", self._add_segment(engine_id))
        request.set_response_handler(self._delete_response_handler)
        self._connection.make_request(request)
        return request

    def delete(self, engine_id: str):
        return self.async_delete(engine_id).get_response()


class QueryClient(BaseClient):
    """
    Client for extracting prediction results from an ActionML Engine
    Instance.
    :param url: the url of the ActionML Engine Instance.
    :param threads: number of threads to handle ActionML API requests. Must be >= 1.
    :param qsize: the max size of the request queue (optional).
        The asynchronous request becomes blocking once this size has been
        reached, until the queued requests are handled.
        Default value is 0, which means infinite queue size.
    :param timeout: timeout for HTTP connection attempts and requests in seconds (optional). Default value is 5.
    """

    def __init__(self, engine_id: str, url: str = "http://localhost:8080", threads=1, qsize=0, timeout=5):
        self.engine_id = engine_id
        self.path = "/engines/{}/queries".format(self.engine_id)
        super(QueryClient, self).__init__(url, threads, qsize, timeout)

    def async_send_query(self, data: dict):
        """
        Asynchronously send a request to the engine instance with data as the query.
        :param data: the query: It is converted to an json object using json.dumps method. type dict.
        :returns: 
            AsyncRequest object. You can call the get_response() method using this
            object to get the final results or status of this asynchronous request.
        """

        request = AsyncRequest("POST", self.path, **data)
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        return request

    def send_query(self, data: dict):
        """
        Synchronously send a request.
        :param data: the query: It is converted to an json object using json.dumps method. type dict.
        :returns: the prediction.
        """
        return self.async_send_query(data).get_response()


class CommandClient(BaseClient):
    def __init__(self, url: str = "http://localhost:8080", threads=1, qsize=0, timeout=5):
        self.path = "/commands/list/"
        super(CommandClient, self).__init__(url, threads, qsize, timeout)

    def async_get_engines_list(self) -> AsyncRequest:
        request = AsyncRequest("GET", self.path + "engines")
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        return request

    def get_engines_list(self) -> object:
        return self.async_get_engines_list().get_response()

    def async_get_datasets_list(self) -> AsyncRequest:
        request = AsyncRequest("GET", self.path + "datasets")
        request.set_response_handler(self._get_response_handler)
        self._connection.make_request(request)
        return request

    def get_datasets_list(self) -> object:
        return self.async_get_datasets_list().get_response()


class FileExporter(object):
    """
    File exporter to write events to JSON file for batch import
    :param file_name: the destination file name
    """

    def __init__(self, file_name):
        """Constructor of Exporter."""
        self._file = open(file_name, 'w')

    def create_event(self, event_id, event, entity_type, entity_id,
                     target_entity_type=None, target_entity_id=None, properties=None,
                     event_time=None, creation_time=None):
        """Create an event and write to the file. (please refer to EventClient's create_event())"""
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

        j = json.dumps(data)
        self._file.write(j + "\n")

    def close(self):
        """
        Close the FileExporter
        Call this method when you finish writing all events to JSON file
        """
        self._file.close()