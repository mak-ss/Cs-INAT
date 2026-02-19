@CloudstreamPlugin
class InatBoxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(InatBox())
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(Dzen())
        registerExtractorAPI(CDNJWPlayer())
    }
}
