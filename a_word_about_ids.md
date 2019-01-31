# Harness Multitenancy

Using different IDs allows Harness to maintain separation of data and to assign permission to use Engine instances.

It is advised that IDs be snake case, using the underscore "_" character to separate long strings or phases used as IDs. This is because most IDs may require being part of a URI and so case is not significant and hyphens are sometimes illegal for certain internal use.

## Exceptions

Any data inside an input event or query which is of type String, such as an event's name, or a user's id, can be any UTF-8 string with any mix of character codes.

## Engine-ids

The Engine-id identifies an Engine Instance and so MUST be unique across a Harness deployment. These correspond to the R in REST&mdash;the resource id. Engines may share a "factory" meaning they are of the same type, but all will have a globally unique id with no exceptions.

## Site, Client, Customer IDs

If using Harness to serve data to multiple Sites or Clients, Harness knows nothing about this separation. In this case is may be good to have some convention for naming Engine Instances that includes the Client ID.

For instance if running Harness to provide a SaaS application and one client has a primary domain name of ABC.com and another client has XYZ.jp.co and they both have Universal Recommender Engine then:

 - The Engine-id for ABC.com might be named `abc_com_ur`
 - The Engine-id for xyz.jp.co might be named `xyz_jp_co_ur`

Or some other convention that keeps the names unique and can be a mnemonic for the type of Engine.

## Shared Data

If 2 Engine Instances can and want to share data, they do this by setting the Engine config to use the same `sharedDBName`. This only affect things that are shareable so consult the Engine specific documentation for what "shareable" means.