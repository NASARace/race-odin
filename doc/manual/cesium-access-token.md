# Cesium Access Token
While not strictly required for all applications it is recommended that you obtain a free cesium access token
from <<https://cesium.com/ion/signup/> to avoid system warnings when accessing some data such as OSM building information.

Once you have this token please enter it to the [Private Configuration](private-configuration.md) vault file:

     secret {
        ...
        cesium {
          access-token="<your-cesium-access-token>"
        }
        ...
     }