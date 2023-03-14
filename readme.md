A plugin for the [Openfire](https://www.igniterealtime.org/projects/openfire/) real time collaboration server that 
implements [Efficient XML Interchange (EXI)](https://www.w3.org/TR/exi/) functionality.

This plugin provides an XMPP implementation of EXI as defined in [XEP-0322](https://xmpp.org/extensions/xep-0322.html).

## Efficient XML Interchange (EXI)

Efficient XML Interchange (EXI) is a binary XML format for exchange of data on a computer network. It is one of the most
prominent efforts to encode XML documents in a binary data format, rather than plain text. Using EXI format reduces the
verbosity of XML documents as well as the cost of parsing. Improvements in the performance of writing (generating) 
content depends on the speed of the medium being written to, the methods and quality of actual implementations.

EXI is useful for:

- a complete range of XML document sizes, from dozens of bytes to terabytes
- reducing computational overhead to speed up parsing of compressed documents
- increasing endurance of small devices by utilizing efficient decompression

Read more about EXI in its [Wikipedia article](https://en.wikipedia.org/wiki/Efficient_XML_Interchange) (where the above
definition was taken from).

## History

This plugin was first developed by Javier Placencio, in 2013 and 2014. In 2023, that now dormant 
[project](https://github.com/javpla/openfire) was forked by the Ignite Realtime community into this project.

