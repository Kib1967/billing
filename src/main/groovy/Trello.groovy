import groovy.json.JsonSlurper

class Trello {

  class Board {
    String id
	String name
  }

  class List {
    String id
	String name
  }

  class Card {
    String id
	String name
  }

  String appKey
  String appToken

  Trello( def appKey, def appToken ) {
    this.appKey = appKey
	this.appToken = appToken
  }
  
  def getBoards() {
    def trelloBoardsUrl = "https://trello.com/1/members/me/boards?key=${appKey}&token=${appToken}&fields=name"
	
	def jsonSlurper = new JsonSlurper()
    def boardInfos = jsonSlurper.parse trelloBoardsUrl.toURL()
	
	def boards = []
	boardInfos.each { boardInfo ->
	  def board = new Board()
	  board.id = boardInfo.id
	  board.name = boardInfo.name
	  
	  boards += board
	}
	
	return boards
  }
  
  def getBoardIdFor(def name) {
    def boards = getBoards()
	def board = boards.find { it.name.equals(name) }
	
	return board ? board.id : null
  }
  
  def getLists(def boardId) {
    def trelloListsUrl = "https://trello.com/1/boards/${boardId}/lists?key=${appKey}&token=${appToken}&fields=name"
	
	def jsonSlurper = new JsonSlurper()
    def listInfos = jsonSlurper.parse trelloListsUrl.toURL()
	
	def lists = []
	listInfos.each { listInfo ->
	  def list = new List()
	  list.id = listInfo.id
	  list.name = listInfo.name
	  
	  lists += list
	}
	
	return lists
  }
  
  def getListIdFor(def name, def boardId) {
    def lists = getLists(boardId)
	def list = lists.find { it.name.equals(name) }
	
	return list ? list.id : null
  }
  
  def getCards(def listId) {
    def trelloCardsUrl = "https://trello.com/1/lists/${listId}/cards?key=${appKey}&token=${appToken}&fields=name"
	
	def jsonSlurper = new JsonSlurper()
    def cardInfos = jsonSlurper.parse trelloCardsUrl.toURL()
	
	def cards = []
	cardInfos.each { cardInfo ->
	  def card = new Card()
	  card.id = cardInfo.id
	  card.name = cardInfo.name
	  
	  cards += card
	}
	
	return cards
  }
}