### BackendPolicy Kind

Another policy [1] to apply endpoint certificates for endpoints in a cluster. 

```
apiVersion: dp.wso2.com/v1alpha1
kind: BackendPolicy
metadata:
  namespace: apk
  name: backendpolicy-sample
spec:
  default:
    tls: 
      enabled: true
      certificate: |
        -----BEGIN CERTIFICATE-----
        <certificate-data>
        -----END CERTIFICATE-----
  targetRef:
    group: ""
    kind: Service
    namespace: apk
    name: web-hook-service
```

Since HTTPRoute has service reference in bakcendRefs when deploying the HTTPRoute, cert can be picked up since targetRef is pointing to a service.

### Envoy config for backend cert and domain validation

Validate an endpoint’s certificates when connecting

### References
[1] https://gateway-api.sigs.k8s.io/references/policy-attachment/#policy-attachment-for-ingress 

[2] https://gateway-api.sigs.k8s.io/geps/gep-1282/?h=contour%E2%80%99s+httpproxy#why-build-something