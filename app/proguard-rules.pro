# FIX #24 (P8 da auditoria): a regra "-keep com.feather.launcher.data.**"
# foi removida — não há reflexão (Gson/Moshi/serialização) usando essas
# classes em lugar nenhum do projeto, então ela só impedia o R8 de
# renomear/otimizar esse pacote à toa, sem nenhum ganho de segurança.
# Adicione regras específicas do projeto aqui, se necessário.
