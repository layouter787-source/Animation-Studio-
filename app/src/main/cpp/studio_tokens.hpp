// Fichas de vida (tokens) para conteúdo que vive no Kotlin mas é referenciado pelo modelo C++ (traços e versões de
// desenho). O C++ guarda só um número; quando a última cópia de um Project que o referencia desaparece, o número
// entra numa fila de libertação, que o Kotlin esvazia (nDrainReleased) para largar o objeto correspondente.
// Sem chamadas JNI a partir de threads arbitrárias e sem referências globais (o Android limita-as a ~51 mil).
#pragma once
#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <utility>
#include <vector>

namespace studio {

struct Token {
    int kind = 0;    // 0 = traço, 1 = versão de desenho
    int64_t id = 0;
};

class TokenTable {
public:
    static TokenTable& instance() {
        static TokenTable t;
        return t;
    }

    /** O mesmo (tipo, id) devolve sempre a mesma ficha enquanto houver alguma cópia viva. */
    std::shared_ptr<const Token> acquire(int kind, int64_t id) {
        std::lock_guard<std::mutex> lock(m_);
        const Key k{kind, id};
        auto it = live_.find(k);
        if (it != live_.end()) {
            if (auto s = it->second.lock()) return s;
        }
        std::shared_ptr<const Token> s(new Token{kind, id}, [this, k](const Token* t) {
            {
                std::lock_guard<std::mutex> l(m_);
                released_.push_back(k);
            }
            delete t;
        });
        live_[k] = s;
        return s;
    }

    /** Fichas que deixaram de existir desde a última chamada, como pares (tipo, id). */
    std::vector<std::pair<int, int64_t>> drain() {
        std::lock_guard<std::mutex> lock(m_);
        std::vector<std::pair<int, int64_t>> out;
        for (const Key& k : released_) {
            auto it = live_.find(k);
            if (it == live_.end() || it->second.expired()) {  // se foi recriada entretanto, ainda vive
                if (it != live_.end()) live_.erase(it);
                out.emplace_back(k.first, k.second);
            }
        }
        released_.clear();
        return out;
    }

    int64_t nextSerial() {
        std::lock_guard<std::mutex> lock(m_);
        return ++serial_;
    }

private:
    using Key = std::pair<int, int64_t>;
    struct KeyHash {
        size_t operator()(const Key& k) const { return std::hash<int64_t>()(k.second * 4 + k.first); }
    };
    std::mutex m_;
    std::unordered_map<Key, std::weak_ptr<const Token>, KeyHash> live_;
    std::vector<Key> released_;
    int64_t serial_ = 0;
};

}  // namespace studio
