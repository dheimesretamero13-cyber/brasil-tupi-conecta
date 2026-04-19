import { MercadoPagoConfig, Preference } from 'mercadopago'

const client = new MercadoPagoConfig({
  accessToken: 'APP_USR-6600302542867792-041912-f120ecfe4f4fe4b8ae4a6d8f4a6fa8e4-3346873824',
})

export async function criarPreferencia(item: {
  titulo: string
  descricao: string
  preco: number
  itemId: string
  clienteId: string
  profissionalId: string
}) {
  const preference = new Preference(client)

  const response = await preference.create({
    body: {
      items: [
        {
          id: item.itemId,
          title: item.titulo,
          description: item.descricao,
          quantity: 1,
          unit_price: item.preco,
          currency_id: 'BRL',
        },
      ],
      back_urls: {
        success: 'http://localhost:5173/success',
        failure: 'http://localhost:5173/failure',
        pending: 'http://localhost:5173/pending',
      },
      auto_return: 'approved',
      metadata: {
        item_id: item.itemId,
        cliente_id: item.clienteId,
        profissional_id: item.profissionalId,
      },
    },
  })

  return response
}