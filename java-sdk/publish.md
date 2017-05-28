1. Create personal access token in github: https://github.com/settings/tokens
need select `repo, user:email`

2. `export GITHUB_OAUTH_TOKEN=6c820194ff994468535e97737b62a2133d03d0c9`

3. `mvn clean deploy -X`

4. Check in: https://github.com/actionml/pio-kappa/tree/mvn-repo/com/actionml/harness-java-sdk