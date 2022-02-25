<#import "upgrader-role.ftl" as upgraderRole>
<#macro cronjob fullDelegateName=delegateName>
<#assign upgraderSaName = "upgrader-cronjob-sa">
<@upgraderRole.cronJobRole upgraderSaName />

---

apiVersion: v1
kind: ConfigMap
metadata:
  name: ${fullDelegateName}-upgrader-config
  namespace: ${delegateNamespace}
data:
  config.yaml: |
    mode: Delegate
    dryRun: false
    workloadName: ${fullDelegateName}
    namespace: ${delegateNamespace}
    containerName: delegate
    delegateConfig:
      accountId: ${accountId}
      managerHost: ${managerHostAndPort}

---

apiVersion: batch/v1beta1
kind: CronJob
metadata:
  labels:
    harness.io/name: ${fullDelegateName}-upgrader-job
  name: ${fullDelegateName}-upgrader-job
  namespace: ${delegateNamespace}
spec:
  schedule: "0 */1 * * *"
  concurrencyPolicy: Forbid
  startingDeadlineSeconds: 20
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: ${upgraderSaName}
          restartPolicy: Never
          containers:
          - image: ${upgraderDockerImage}
            name: upgrader
            imagePullPolicy: Always
            envFrom:
            - secretRef:
                name: ${accountTokenName}
            volumeMounts:
              - name: config-volume
                mountPath: /etc/config
          volumes:
            - name: config-volume
              configMap:
                name: ${fullDelegateName}-upgrader-config
</#macro>