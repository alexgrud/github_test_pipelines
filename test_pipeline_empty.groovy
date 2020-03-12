common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
node{
    stage ("Test stage") {
        println("Test message")
    }
}
