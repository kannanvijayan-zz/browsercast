
import time
import logging
import signal

from HandshakeThread import HandshakeThread

LOG_LEVEL = logging.INFO

def main():
    logging.getLogger().setLevel(LOG_LEVEL)

    handshake_thread = HandshakeThread(2015)
    logging.info('Starting handshake thread.')
    handshake_thread.start()

    waitForComplete([handshake_thread])

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
