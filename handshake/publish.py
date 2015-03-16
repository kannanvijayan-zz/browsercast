
import time
import gobject
import logging
import signal
from dbus.mainloop import glib

from ServiceThread import ServiceThread

LOG_LEVEL = logging.INFO

def main():
    logging.getLogger().setLevel(LOG_LEVEL)
    glib.threads_init()

    service_thread = ServiceThread('Television', 2015)
    logging.info('Starting service thread.')
    service_thread.start()

    waitForComplete([service_thread])

def waitForComplete(thread_list):

    def handleInterrupt(*args):
        for thr in thread_list:
            thr.requestStop()
    signal.signal(signal.SIGINT, handleInterrupt)

    logging.info('Waiting for threads to complete.')
    while True:
        threadsDone = True

        for thr in thread_list:
            if thr.isAlive():
                threadsDone = False

        if threadsDone:
            break

        time.sleep(0.25)

if __name__ == '__main__':
    main()
