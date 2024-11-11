import sys
import chess.syzygy

# pip install python-chess

def probe_tablebase(board):
  tablebase = chess.syzygy.open_tablebase("tablebase/zyzygy_tables")
  wdl = tablebase.probe_wdl(board)
  dtz = tablebase.probe_dtz(board)
  tablebase.close()
  return [wdl, dtz]

def main():
  fen = sys.argv[1]
  board = chess.Board(fen)
  [wdl, dtz] = probe_tablebase(board)
  print(wdl)
  print(dtz)

if __name__ == '__main__':
  main()
