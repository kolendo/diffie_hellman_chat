# Client-server chat with Diffie-Hellman key exchange

* Implemented GUI chat based on sockets in Java. :shipit:
* Server supports multiple clients at the same time (up to 10).
* All data is sent via JSON structure.
* At connection startup the Diffie-Hellman protocol is initialized.
* DH values are generated randomly.
* All messages are encoded in Base64 scheme.
* The key value from DH is used for additional encryption of messages (chosen by user):
  * None
  * XOR cipher
  * Caesar cipher
