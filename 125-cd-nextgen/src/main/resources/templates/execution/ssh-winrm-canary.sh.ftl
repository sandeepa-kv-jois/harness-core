${failureStrategies}
<#list phases as phase>
<#if phase_index=0>
${canarySnippet
?replace("<+start>", 0)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))
?replace("<+setup_runtime_paths_script>", setupRuntimePathsScript)
?replace("<+setup_runtime_paths_script_war>", setupRuntimePathsScriptWar)
?replace("<+process_stop_script>", processStopScript)
?replace("<+port_cleared_script>", portClearedScript)
?replace("<+process_run_script>", processRunScript)
?replace("<+port_listening_script>", portListeningScript)
?replace("<+extend_artifact_script_powershell>", extendArtifactScriptPS)
?replace("<+create_apppool_script_powershell>", createAppPoolScriptPS)
?replace("<+create_website_script_powershell>", createWebsiteScriptPS)
?replace("<+create_virtual_directory_script_powershell>", createVirtualDirectoryScriptPS)
?replace("<+setup_runtime_paths_script_powershell>", setupRuntimePathsScriptPS)}
<#assign prevPhase = phase>
<#else>
${canarySnippet
?replace("spec:\n  execution:\n    steps:\n", "")
?replace("<+start>", prevPhase)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))
?replace("<+setup_runtime_paths_script>", setupRuntimePathsScript)
?replace("<+setup_runtime_paths_script_war>", setupRuntimePathsScriptWar)
?replace("<+process_stop_script>", processStopScript)
?replace("<+port_cleared_script>", portClearedScript)
?replace("<+process_run_script>", processRunScript)
?replace("<+port_listening_script>", portListeningScript)
?replace("<+extend_artifact_script_powershell>", extendArtifactScriptPS)
?replace("<+create_apppool_script_powershell>", createAppPoolScriptPS)
?replace("<+create_website_script_powershell>", createWebsiteScriptPS)
?replace("<+create_virtual_directory_script_powershell>", createVirtualDirectoryScriptPS)
?replace("<+setup_runtime_paths_script_powershell>", setupRuntimePathsScriptPS)}
<#assign prevPhase = phase>
</#if>
</#list>
    rollbackSteps:
<#list phases as phase>
<#if phase_index=0>
${canaryRollbackSnippet
?replace("spec:\n  execution:\n    rollbackSteps:\n", "")
?replace("<+start>", 0)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))
?replace("<+setup_runtime_paths_script>", setupRuntimePathsScript)
?replace("<+setup_runtime_paths_script_war>", setupRuntimePathsScriptWar)
?replace("<+process_stop_script>", processStopScript)
?replace("<+port_cleared_script>", portClearedScript)
?replace("<+process_run_script>", processRunScript)
?replace("<+port_listening_script>", portListeningScript)
?replace("<+extend_artifact_script_powershell>", extendArtifactScriptPS)
?replace("<+create_apppool_script_powershell>", createAppPoolScriptPS)
?replace("<+create_website_script_powershell>", createWebsiteScriptPS)
?replace("<+create_virtual_directory_script_powershell>", createVirtualDirectoryScriptPS)
?replace("<+setup_runtime_paths_script_powershell>", setupRuntimePathsScriptPS)}
<#assign prevPhase = phase>
<#else>
${canaryRollbackSnippet
?replace("spec:\n  execution:\n    rollbackSteps:\n", "")
?replace("<+start>", prevPhase)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))
?replace("<+setup_runtime_paths_script>", setupRuntimePathsScript)
?replace("<+setup_runtime_paths_script_war>", setupRuntimePathsScriptWar)
?replace("<+process_stop_script>", processStopScript)
?replace("<+port_cleared_script>", portClearedScript)
?replace("<+process_run_script>", processRunScript)
?replace("<+port_listening_script>", portListeningScript)
?replace("<+extend_artifact_script_powershell>", extendArtifactScriptPS)
?replace("<+create_apppool_script_powershell>", createAppPoolScriptPS)
?replace("<+create_website_script_powershell>", createWebsiteScriptPS)
?replace("<+create_virtual_directory_script_powershell>", createVirtualDirectoryScriptPS)
?replace("<+setup_runtime_paths_script_powershell>", setupRuntimePathsScriptPS)}
<#assign prevPhase = phase>
</#if>
</#list>