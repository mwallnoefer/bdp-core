pipeline {
    agent any
    
    environment {
        SERVER_PORT="1010"
        PROJECT = "odh-writer"
        PROJECT_FOLDER = "writer"
        ARTIFACT_NAME = "writer"
        DOCKER_IMAGE = '755952719952.dkr.ecr.eu-west-1.amazonaws.com/odh-writer'
        DOCKER_TAG = "test-$BUILD_NUMBER"
        BDP_WRITER_KEYCLOAK_CONFIG = credentials('bigdataplatform-writer-keycloak.json')
        BDP_DATABASE_SCHEMA = "intimev2"
        BDP_DATABASE_HOST = "test-pg-bdp.co90ybcr8iim.eu-west-1.rds.amazonaws.com"
        BDP_DATABASE_PORT = "5432"
        BDP_DATABASE_NAME = "bdp"
        BDP_DATABASE_WRITE_USER = "bdp"
        BDP_DATABASE_WRITE_PASSWORD = credentials('bdp-core-test-database-write-password')
    }
    parameters{
        string(name:'bdp_version',defaultValue:'x.y.z',description:'version of dependencies to use in test deployment(must be released)');
        choice(name:'bdp_type',choices:['snapshot','release'],description:'use production ready releases or snapshots')
    }
    stages {
        stage('Configure') {
            steps {
                sh 'cp dal/src/main/resources/META-INF/persistence.xml.dist dal/src/main/resources/META-INF/persistence.xml'
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.default_schema']/@value" -v ${BDP_DATABASE_SCHEMA} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.hikari.dataSource.serverName']/@value" -v ${BDP_DATABASE_HOST} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.hikari.dataSource.portNumber']/@value" -v ${BDP_DATABASE_PORT} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit/_:properties/_:property[@name='hibernate.hikari.dataSource.databaseName']/@value" -v ${BDP_DATABASE_NAME} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit[@name='jpa-persistence-write']/_:properties/_:property[@name='hibernate.hikari.dataSource.user']/@value" -v ${BDP_DATABASE_WRITE_USER} dal/src/main/resources/META-INF/persistence.xml'''
                sh '''xmlstarlet ed -L -u "//_:persistence-unit[@name='jpa-persistence-write']/_:properties/_:property[@name='hibernate.hikari.dataSource.password']/@value" -v ${BDP_DATABASE_WRITE_PASSWORD} dal/src/main/resources/META-INF/persistence.xml'''
                sh "./quickrelease.sh '${params.bdp_type}' '${params.bdp_version}'"
                sh """
                    cd ${PROJECT_FOLDER}
                    echo 'SERVER_PORT=${SERVER_PORT}' > .env
                    echo 'DOCKER_IMAGE=${DOCKER_IMAGE}' >> .env
                    echo 'DOCKER_TAG=${DOCKER_TAG}' >> .env
                    echo 'LOG_LEVEL=info' >> .env
                    echo 'ARTIFACT_NAME=${ARTIFACT_NAME}' >> .env
                    echo 'COMPOSE_PROJECT_NAME=${PROJECT}' >> .env
                """
                sh 'cat ${BDP_WRITER_KEYCLOAK_CONFIG} > writer/src/main/resources/keycloak.json'
            }
        }
        stage('Test & Build') {
            steps {
                sh """
                    aws ecr get-login --region eu-west-1 --no-include-email | bash
                    docker-compose --no-ansi -f ${PROJECT_FOLDER}/infrastructure/docker-compose.build.yml build --pull
                    docker-compose --no-ansi -f ${PROJECT_FOLDER}/infrastructure/docker-compose.build.yml push
                """
            }
        }
        stage('Deploy') {
            steps {
               sshagent(['jenkins-ssh-key']) {
                    sh """
                        (cd ${PROJECT_FOLDER}/infrastructure/ansible && ansible-galaxy install -f -r requirements.yml)
                        (cd ${PROJECT_FOLDER}/infrastructure/ansible && ansible-playbook --limit=test deploy.yml --extra-vars "release_name=${BUILD_NUMBER}")
                    """
                }
            }
        }
    }
}
