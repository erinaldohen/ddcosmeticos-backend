package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Serviço responsável por manipular arquivos físicos (Uploads).
 */
@Service
public class ArquivoService {

    // Define a pasta raiz onde as imagens serão salvas
    private final Path diretorioUploads = Paths.get("uploads");

    // Construtor: Cria a pasta 'uploads' se ela não existir ao iniciar a aplicação
    public ArquivoService() {
        try {
            Files.createDirectories(diretorioUploads);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório de uploads.", e);
        }
    }

    /**
     * Salva um arquivo no disco e retorna o nome gerado.
     * @param arquivo O arquivo recebido via upload.
     * @return O nome do arquivo salvo (com UUID para ser único).
     */
    public String salvarImagem(MultipartFile arquivo) {
        try {
            if (arquivo.isEmpty()) {
                throw new RuntimeException("Arquivo vazio. Por favor envie uma imagem válida.");
            }

            // Gera um nome único para evitar que uma imagem sobrescreva outra com o mesmo nome
            // Exemplo: "ab12-cd34-ef56_foto-produto.jpg"
            String nomeArquivo = UUID.randomUUID().toString() + "_" + arquivo.getOriginalFilename();

            // Define o caminho completo do arquivo
            Path destino = diretorioUploads.resolve(nomeArquivo);

            // Copia os bytes do arquivo para o disco (substituindo se existir conflito, o que é raro com UUID)
            Files.copy(arquivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            return nomeArquivo;
        } catch (IOException e) {
            throw new RuntimeException("Erro crítico ao salvar arquivo no servidor.", e);
        }
    }
}