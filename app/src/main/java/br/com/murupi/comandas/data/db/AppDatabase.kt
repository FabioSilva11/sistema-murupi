package br.com.murupi.comandas.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.ConfigRestaurante
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.Produto

@Database(
    entities = [Categoria::class, Produto::class, Comanda::class, ItemComanda::class, Impressora::class, ConfigRestaurante::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun produtoDao(): ProdutoDao
    abstract fun comandaDao(): ComandaDao
    abstract fun itemComandaDao(): ItemComandaDao
    abstract fun impressoraDao(): ImpressoraDao
    abstract fun configDao(): ConfigDao

    companion object {
        private const val NOME_ARQUIVO = "murupi.db"

        /**
         * Versão 2: grupo, estoque, ativo e múltiplo de venda nos produtos; sucos do cardápio
         * agrupados por fruta; entradas vendidas de 5 em 5.
         */
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE produtos ADD COLUMN grupo TEXT")
                db.execSQL("ALTER TABLE produtos ADD COLUMN estoque INTEGER")
                db.execSQL("ALTER TABLE produtos ADD COLUMN ativo INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE produtos ADD COLUMN multiplo INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE itens_comanda ADD COLUMN multiplo INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "UPDATE produtos SET multiplo = 5 WHERE categoriaId IN (SELECT id FROM categorias WHERE nome = 'ENTRADAS')"
                )
                db.execSQL(
                    """
                    UPDATE produtos
                    SET grupo = trim(replace(replace(replace(nome, 'Suco de ', ''), ' 300ml', ''), ' 500ml', ''))
                    WHERE nome LIKE 'Suco de % 300ml' OR nome LIKE 'Suco de % 500ml'
                    """
                )
                db.execSQL("UPDATE categorias SET perguntarOpcoesSuco = 1 WHERE nome = 'SUCOS'")
            }
        }

        /**
         * Versão 3: composição do produto, exibida abaixo do nome no lançamento.
         */
        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE produtos ADD COLUMN descricao TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Versão 4: balcão e delivery (tipo, nome do cliente e endereço na comanda).
         */
        val MIGRACAO_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comandas ADD COLUMN tipo TEXT NOT NULL DEFAULT 'MESA'")
                db.execSQL("ALTER TABLE comandas ADD COLUMN nomeCliente TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE comandas ADD COLUMN endereco TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Versão 5: sem papel de refrigerantes (só Espelho, Cozinha e Sucos).
         * Impressoras antigas de refrigerantes viram cozinha.
         */
        val MIGRACAO_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE impressoras SET papel = 'COZINHA' WHERE papel = 'REFRIGERANTES'")
            }
        }

        /**
         * Versão 6: configuração do restaurante (nome, agradecimento, total de mesas
         * e taxa de serviço) numa tabela de linha única, com os padrões antigos.
         */
        val MIGRACAO_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS config_restaurante (
                        id INTEGER NOT NULL PRIMARY KEY,
                        nome TEXT NOT NULL,
                        agradecimento TEXT NOT NULL,
                        totalMesas INTEGER NOT NULL,
                        taxaServicoPercentual INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO config_restaurante (id, nome, agradecimento, totalMesas, taxaServicoPercentual) " +
                        "VALUES (1, 'MURUPI RESTAURANTE', 'O Restaurante Murupi agradece a preferência.', 60, 0)"
                )
            }
        }

        /** Versão 7: preserva todas as formas usadas no pagamento dividido de uma conta. */
        val MIGRACAO_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comandas ADD COLUMN formasPagamentoCsv TEXT")
            }
        }

        /** Versão 8: preserva cancelamentos de itens que já chegaram à cozinha. */
        val MIGRACAO_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE itens_comanda ADD COLUMN cancelado INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE itens_comanda ADD COLUMN canceladoEm INTEGER")
                db.execSQL("ALTER TABLE itens_comanda ADD COLUMN cancelamentoPendenteImpressao INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOME_ARQUIVO)
                .addMigrations(
                    MIGRACAO_1_2,
                    MIGRACAO_2_3,
                    MIGRACAO_3_4,
                    MIGRACAO_4_5,
                    MIGRACAO_5_6,
                    MIGRACAO_6_7,
                    MIGRACAO_7_8
                )
                .build()
    }
}
