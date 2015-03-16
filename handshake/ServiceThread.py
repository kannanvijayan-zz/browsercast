
import dbus
import gobject
import avahi
from dbus.mainloop import glib
import threading
import logging
from encodings import idna

LOG = logging.getLogger()
CLASS_IN = 0x1
TYPE_CNAME = 0x5
TTL = 60

class ServiceThread(threading.Thread):
    DefaultServiceType = '_browsercast._tcp'
    DefaultServiceTxt = ''
    DefaultDomain = ''
    DefaultHost = ''

    def __init__(self, name, port):
        super(ServiceThread, self).__init__()

        self.serviceName = name
        self.servicePort = port
        self.serviceType = self.DefaultServiceType
        self.serviceTxt = self.DefaultServiceTxt
        self.domain = self.DefaultDomain
        self.host = self.DefaultHost
        self.group = None
        self.renameCount = 10
        self.attemptedRenames = 0

        glib.DBusGMainLoop(set_as_default=True)

        self.mainLoop = gobject.MainLoop()
        gobject.timeout_add(250, self.checkIfStopRequested)
        self.bus = dbus.SystemBus()

        self.stopLock = threading.Lock()
        self.stopRequested = False

        server_obj = self.bus.get_object(avahi.DBUS_NAME, avahi.DBUS_PATH_SERVER)
        self.server = dbus.Interface(server_obj, avahi.DBUS_INTERFACE_SERVER)
        self.server.connect_to_signal('StateChanged', self.serverStateChanged)
        self.serverStateChanged(self.server.GetState())

    def requestStop(self):
        logging.info("Requesting that ServiceThread be stopped.")
        self.stopLock.acquire()
        self.stopRequested = True
        self.stopLock.release()

    def checkIfStopRequested(self):
        stopReq = False
        self.stopLock.acquire()
        stopReq = self.stopRequested
        self.stopLock.release()

        if stopReq:
            logging.info("ServiceThread got stop-request.")
            self.removeService()
            self.mainLoop.quit()
            return False
        return True

    def getGroup(self):
        if self.group is None:
            logging.info("Creating dbus entry group.")
            group_obj = self.bus.get_object(avahi.DBUS_NAME, self.server.EntryGroupNew())
            self.group = dbus.Interface(group_obj, avahi.DBUS_INTERFACE_ENTRY_GROUP)
            self.group.connect_to_signal('StateChanged', self.entryGroupStateChanged)
        return self.group

    def attemptRename(self):
        if self.attemptedRenames >= self.renameCount:
            return False
        self.attemptedRnames += 1
        return True

    def run(self):
        logging.info('ServiceThread.run - starting main loop.')
        self.mainLoop.run()

    def addService(self):
        group = self.getGroup()
        logging.info("Adding service '%s' of type '%s'" %
                     (self.serviceName, self.serviceType))

        servname = self.serviceName + '.local'
        hostname = str(self.server.GetHostNameFqdn())
        logging.info("Adding address '%s' => '%s'\n", servname, hostname)

        group.AddService(avahi.IF_UNSPEC, avahi.PROTO_UNSPEC, dbus.UInt32(0),
                         self.serviceName, self.serviceType,
                         self.domain, self.host,
                         dbus.UInt16(self.servicePort),
                         avahi.string_array_to_txt_array(self.serviceTxt))

        #cname = self.EncodeDNS(servname)
        #rdata = self.CreateRR(hostname)
        #group.AddRecord(avahi.IF_UNSPEC, avahi.PROTO_UNSPEC, dbus.UInt32(0),
        #                cname, CLASS_IN, TYPE_CNAME, TTL, rdata)
        group.Commit()

    @staticmethod
    def CreateRR(name):
        out = []
        for part in name.split('.'):
            if len(part) == 0:
                continue
            part_ascii = idna.ToASCII(part)
            out.append(chr(len(part_ascii)))
            out.append(part_ascii)
        out.append('\0')
        return ''.join(out)

    @staticmethod
    def EncodeDNS(name):
        out = []
        for part in name.split('.'):
            if len(part) == 0:
                continue
            out.append(idna.ToASCII(part))
        return '.'.join(out)

    def removeService(self):
        if self.group is None:
            return
        self.group.Reset()

    def entryGroupStateChanged(self, state, error):
        logging.info("State change: %i" % (state,))

        if state == avahi.ENTRY_GROUP_ESTABLISHED:
            logging.info('Service established.')

        elif state == avahi.ENTRY_GROUP_COLLISION:
            if self.attemptRename():
                newName = self.server.GetAlternativeServiceName(name)
                logging.warn("Service name collition for '%s', trying '%s'" %
                             (self.serviceName, newName))
                self.serviceName = newName
                self.removeService()
                self.addService()

        elif state == avahi.ENTRY_GROUP_FAILURE:
            logging.error("Error in group state change: %s" % (error,))
            self.mainLoop.quit()
            return

    def serverStateChanged(self, state):
        logging.info("Server state changed: %s" % (state,))

        if state == avahi.SERVER_COLLISION:
            logging.error("Server name collition!")
            self.removeService()

        elif state == avahi.SERVER_RUNNING:
            self.addService()
