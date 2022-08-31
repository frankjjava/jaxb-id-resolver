JAXB ID Resolver:
------------------
//TODO - this is a work-in-progress.

JAXB objects though meant to represent XML documents, you may already know that they still can used them to generate JSON. 
Jackson library's ObjectMapper can be used to serialize and deserailize JAXB objects. However one challenge that we would face is with regard to is JAXB's ID and IDref annotated fields.

The relationship between the ID and the IDRef fields are akin to primary-key and foreign-key relationship in an RDBMS. The IDRef field is like a foreign key to an XML document fragment identified as an ID field somewhere in the same document. The IDRef field in the JAXB object should have the reference to the ID field only and not the entire document repeated as another object. 

The JAXB marshaller / unmarshaller takes care of this. But if we use Jackson ObjectMapper, it does not keep a reference to the orginal ID field, but rather creates another Java object. This can cause issues when you want to process JAXB objects in your software application because both of these are 2 different objects instead of referring to the same object.

This "JAXB ID Resolver" library fixes such referencing issues if in case you choose to use Jackson's ObjectMapper for deserializations.